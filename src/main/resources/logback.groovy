import ch.qos.logback.core.util.FileSize

appender("ROLLING", RollingFileAppender) {
    def HOME_DIR = "."
    encoder(PatternLayoutEncoder) {
        pattern = "%date %level %logger{0} [%file:%line] %msg%n"
    }
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = "${HOME_DIR}/logs/SoundMan_%d{yyyy-MM-dd_HH-mm}.log"
        maxHistory = 20
        totalSizeCap = FileSize.valueOf("1GB")
    }
}
root(INFO, ["ROLLING"])