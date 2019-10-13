import static ch.qos.logback.classic.Level.INFO

appender("FILE", RollingFileAppender) {
    file = "logs/SoundMan.log"
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = "logs/SoundMan_%d{yyyy-MM-dd}.log"
        maxHistory = 10
        totalSizeCap = "1KB"
    }
    encoder(PatternLayoutEncoder) {
        pattern = "%date %level %logger{0} [%file:%line] %msg%n"
    }
}
root(INFO, ["FILE"])