module dorkbox.executor {
    exports dorkbox.executor;
    exports dorkbox.executor.exceptions;
    exports dorkbox.executor.listener;
    exports dorkbox.executor.processResults;
    exports dorkbox.executor.stop;
    exports dorkbox.executor.stream;
    exports dorkbox.executor.stream.nopStreams;
    exports dorkbox.executor.stream.slf4j;

    requires dorkbox.updates;
    requires org.slf4j;

    requires static sshj;
    requires static ch.qos.logback.classic;

    requires kotlin.stdlib;
    requires java.base;
}
