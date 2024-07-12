package us.abstracta.jmeter.javadsl.core.threadgroups.defaultthreadgroup;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.threads.gui.ThreadGroupGui;
import us.abstracta.jmeter.javadsl.codegeneration.MethodCall;
import us.abstracta.jmeter.javadsl.codegeneration.MethodCallBuilder;
import us.abstracta.jmeter.javadsl.codegeneration.MethodCallContext;
import us.abstracta.jmeter.javadsl.codegeneration.MethodParam;
import us.abstracta.jmeter.javadsl.codegeneration.TestElementParamBuilder;
import us.abstracta.jmeter.javadsl.codegeneration.params.ChildrenParam;
import us.abstracta.jmeter.javadsl.codegeneration.params.DurationParam;
import us.abstracta.jmeter.javadsl.codegeneration.params.IntParam;
import us.abstracta.jmeter.javadsl.codegeneration.params.StringParam;
import us.abstracta.jmeter.javadsl.core.threadgroups.BaseThreadGroup;
import us.abstracta.jmeter.javadsl.core.threadgroups.DslDefaultThreadGroup;
import us.abstracta.jmeter.javadsl.core.threadgroups.config.ThreadGroupConfig;
import us.abstracta.jmeter.javadsl.core.util.JmeterFunction;

public class SimpleThreadGroupHelper extends BaseThreadGroup<DslDefaultThreadGroup> {

  private static final Integer ZERO = 0;
  private final List<Stage> stages;

  public SimpleThreadGroupHelper(List<Stage> stages) {
    super(null, ThreadGroupGui.class, Collections.emptyList());
    this.stages = stages;
  }

  @Override
  public AbstractThreadGroup buildThreadGroup() {
    if (stages.isEmpty()) {
      ThreadGroupConfig threadGroupConfig = new ThreadGroupConfig(1, 1, null, null, null);
      return buildSimpleThreadGroupFrom(threadGroupConfig);
    }

    ThreadGroupParams params = initializeParamsFromFirstStage(stages.get(0));

    if (stages.size() > 1) {
      updateParamsForSecondStage(params, stages.get(1));
      if (isZeroThreadCount(stages.get(0)) && stages.size() > 2) {
        updateParamsForThirdStage(params, stages.get(2));
      }
    }

    params.duration = adjustDurationForRampUpPeriod(params.rampUpPeriod, params.iterations, params.duration);

    return buildSimpleThreadGroupFrom(new ThreadGroupConfig(params.threads, params.iterations, params.rampUpPeriod, params.duration, params.delay));
  }

  private ThreadGroupParams initializeParamsFromFirstStage(Stage firstStage) {
    ThreadGroupParams params = new ThreadGroupParams();
    if (isZeroThreadCount(firstStage)) {
      params.delay = firstStage.duration();
    } else {
      params.threads = firstStage.threadCount();
      params.iterations = firstStage.iterations();
      if (firstStage.iterations() == null) {
        params.rampUpPeriod = firstStage.duration();
      } else {
        params.duration = firstStage.duration();
      }
    }
    return params;
  }

  private void updateParamsForSecondStage(ThreadGroupParams params, Stage secondStage) {
    params.threads = secondStage.threadCount();
    params.iterations = secondStage.iterations();
    if (isZeroThreadCount(stages.get(0))) {
      params.rampUpPeriod = secondStage.duration();
    } else {
      params.duration = secondStage.duration();
    }
  }

  private void updateParamsForThirdStage(ThreadGroupParams params, Stage thirdStage) {
    params.duration = thirdStage.duration();
    params.iterations = thirdStage.iterations();
  }

  private boolean isZeroThreadCount(Stage stage) {
    return ZERO.equals(stage.threadCount());
  }

  private Object adjustDurationForRampUpPeriod(Object rampUpPeriod, Object iterations, Object duration) {
    if (rampUpPeriod != null && !Duration.ZERO.equals(rampUpPeriod) && (iterations == null || duration != null)) {
      return duration != null ? sumDurations(duration, rampUpPeriod) : rampUpPeriod;
    }
    return duration;
  }

  private class ThreadGroupParams {
    Object threads = 1;
    Object iterations = 1;
    Object rampUpPeriod = null;
    Object duration = null;
    Object delay = null;
  }


  private Object sumDurations(Object duration, Object rampUpPeriod) {
    if (duration instanceof Duration && rampUpPeriod instanceof Duration) {
      return ((Duration) duration).plus((Duration) rampUpPeriod);
    } else {
      if (duration instanceof Duration) {
        duration = String.valueOf(durationToSeconds((Duration) duration));
      } else if (rampUpPeriod instanceof Duration) {
        rampUpPeriod = String.valueOf(durationToSeconds((Duration) rampUpPeriod));
      }
      return JmeterFunction.groovy(buildGroovySolvingIntExpression((String) duration) + " + "
          + buildGroovySolvingIntExpression((String) rampUpPeriod));
    }
  }

  private static String buildGroovySolvingIntExpression(String expr) {
    /*
     * Replacing $ with # (or alternative, depending on level of nesting of groovy expression)
     * to avoid Jmeter to interpret this property, and delegate evaluation to CompoundVariable for
     * proper calculation.
     */
    StringBuilder altPlaceHolder = new StringBuilder("#");
    while (expr.contains(altPlaceHolder + "{")) {
      altPlaceHolder.append("#");
    }
    return "(new org.apache.jmeter.engine.util.CompoundVariable('"
        + expr.replace("${", altPlaceHolder + "{")
        // Escape chars that are unescaped by groovy script
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        + "'.replace('" + altPlaceHolder + "','$')).execute() as int)";
  }

  private ThreadGroup buildSimpleThreadGroupFrom(ThreadGroupConfig config) {
    ThreadGroup ret = new ThreadGroup();
    Object configThreads = config.getThreads();
    Object configIterations = config.getIterations();
    Object configRampUpPeriod = config.getRampUpPeriod();
    Object configDuration = config.getDuration();
    Object configDelay = config.getDelay();

    setIntProperty(ret, ThreadGroup.NUM_THREADS, configThreads);
    setIntProperty(ret, ThreadGroup.RAMP_TIME, configRampUpPeriod == null ? Duration.ZERO : configRampUpPeriod);
    LoopController loopController = new LoopController();
    ret.setSamplerController(loopController);
    if (configIterations == null) {
      loopController.setLoops(-1);
    } else {
      setIntProperty(loopController, LoopController.LOOPS, configIterations);
    }
    if (configDuration != null) {
      setLongProperty(ret, ThreadGroup.DURATION, configDuration);
    }
    if (configDelay != null) {
      setLongProperty(ret, ThreadGroup.DELAY, configDelay);
    }
    if (configDuration != null || configDelay != null) {
      ret.setScheduler(true);
    }
    ret.setIsSameUserOnNextIteration(false);
    return ret;
  }

  private void setIntProperty(TestElement ret, String propName, Object value) {
    if (value instanceof Duration) {
      ret.setProperty(propName, (int) durationToSeconds((Duration) value));
    } else if (value instanceof Integer) {
      ret.setProperty(propName, (Integer) value);
    } else {
      ret.setProperty(propName, (String) value);
    }
  }

  private void setLongProperty(TestElement ret, String propName, Object value) {
    if (value instanceof Duration) {
      ret.setProperty(propName, durationToSeconds((Duration) value));
    } else {
      ret.setProperty(propName, (String) value);
    }
  }

  public static class CodeBuilder extends MethodCallBuilder {

    public CodeBuilder(List<Method> builderMethods) {
      super(builderMethods);
    }

    @Override
    public boolean matches(MethodCallContext context) {
      return false;
    }

    @Override
    public MethodCall buildMethodCall(MethodCallContext context) {
      TestElementParamBuilder testElement = new TestElementParamBuilder(context.getTestElement());
      MethodParam name = testElement.nameParam("Thread Group");
      MethodParam threads = testElement.intParam(ThreadGroup.NUM_THREADS);
      MethodParam rampTime = testElement.durationParam(ThreadGroup.RAMP_TIME,
          Duration.ofSeconds(1));
      MethodParam duration = testElement.durationParam(ThreadGroup.DURATION);
      MethodParam delay = testElement.durationParam(ThreadGroup.DELAY);
      MethodParam iterations = testElement.intParam(
          ThreadGroup.MAIN_CONTROLLER + "/" + LoopController.LOOPS, -1);
      if (threads instanceof IntParam && duration instanceof DurationParam
          && iterations instanceof IntParam && isDefaultOrZeroDuration(rampTime)
          && isDefaultOrZeroDuration(delay) && (isDefaultOrZeroDuration(duration)
          || iterations.isDefault())) {
        return buildMethodCall(name, threads,
            isDefaultOrZeroDuration(duration) ? iterations : duration,
            new ChildrenParam<>(ThreadGroupChild[].class));
      } else {
        if (!(threads instanceof IntParam) || !(rampTime instanceof DurationParam)
            || !(duration instanceof DurationParam)) {
          threads = new StringParam(threads.getExpression());
          rampTime = new StringParam(rampTime.getExpression());
          duration = new StringParam(duration.getExpression());
        }
        MethodCall ret = buildMethodCall(name);
        if (!isDefaultOrZeroDuration(delay)) {
          ret.chain("holdFor", delay);
        }
        if (!iterations.isDefault() || isDefaultOrZeroDuration(duration)) {
          ret.chain("rampTo", threads, rampTime)
              .chain("holdIterating", !iterations.isDefault() ? iterations : new IntParam(-1));
          if (!isDefaultOrZeroDuration(duration)) {
            duration = buildDurationParam(duration, rampTime, ret);
            ret.chain("upTo", duration);
          }
        } else {
          duration = buildDurationParam(duration, rampTime, ret);
          ret.chain("rampToAndHold", threads, rampTime, duration);
        }
        return ret;
      }
    }

    private boolean isDefaultOrZeroDuration(MethodParam duration) {
      return duration.isDefault()
          || duration instanceof DurationParam && ((DurationParam) duration).getValue().isZero();
    }

    private MethodParam buildDurationParam(MethodParam duration, MethodParam rampTime,
        MethodCall ret) {
      if (duration instanceof DurationParam && rampTime instanceof DurationParam) {
        return new DurationParam(rampTime.isDefault() ? ((DurationParam) duration).getValue()
            : ((DurationParam) duration).getValue().minus(((DurationParam) rampTime).getValue()));
      } else {
        if (!isDefaultOrZeroDuration(rampTime)) {
          ret.chainComment("To keep generated DSL simple, the original duration is used as hold "
              + "for time. But, you should use as hold for time the original duration - ramp up "
              + "period.");
        }
        return duration;
      }
    }

  }

}
