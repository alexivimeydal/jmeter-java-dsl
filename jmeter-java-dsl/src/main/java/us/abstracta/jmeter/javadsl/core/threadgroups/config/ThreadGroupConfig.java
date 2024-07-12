package us.abstracta.jmeter.javadsl.core.threadgroups.config;

public class ThreadGroupConfig {
    private Object threads;
    private Object iterations;
    private Object rampUpPeriod;
    private Object duration;
    private Object delay;

    // Constructors
    public ThreadGroupConfig() {}

    public ThreadGroupConfig(Object threads, Object iterations, Object rampUpPeriod, Object duration, Object delay) {
        this.threads = threads;
        this.iterations = iterations;
        this.rampUpPeriod = rampUpPeriod;
        this.duration = duration;
        this.delay = delay;
    }

    // Getters and setters
    public Object getThreads() {
        return threads;
    }

    public void setThreads(Object threads) {
        this.threads = threads;
    }

    public Object getIterations() {
        return iterations;
    }

    public void setIterations(Object iterations) {
        this.iterations = iterations;
    }

    public Object getRampUpPeriod() {
        return rampUpPeriod;
    }

    public void setRampUpPeriod(Object rampUpPeriod) {
        this.rampUpPeriod = rampUpPeriod;
    }

    public Object getDuration() {
        return duration;
    }

    public void setDuration(Object duration) {
        this.duration = duration;
    }

    public Object getDelay() {
        return delay;
    }

    public void setDelay(Object delay) {
        this.delay = delay;
    }
}
