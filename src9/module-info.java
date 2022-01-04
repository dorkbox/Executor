module dorkbox.executor {
    exports dorkbox.executor;
    exports dorkbox.executor.exceptions;
    exports dorkbox.executor.listener;
    exports dorkbox.executor.processResults;
    exports dorkbox.executor.stop;
    exports dorkbox.executor.stream;
    exports dorkbox.executor.stream.nopStreams;
    exports dorkbox.executor.stream.slf4j;

    requires transitive dorkbox.updates;
    requires transitive org.slf4j;

    requires static com.hierynomus.sshj;
    requires static ch.qos.logback.classic;

    requires transitive kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
}
