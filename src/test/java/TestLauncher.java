import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import javax.security.auth.callback.LanguageCallback;
import java.io.PrintWriter;
import java.util.logging.SocketHandler;

public class TestLauncher {
    public static void main(String[] args) {
        var summaryGeneratingListener = new SummaryGeneratingListener();

        Launcher launcher = LauncherFactory.create();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()

                .selectors(DiscoverySelectors.selectClass(DataFetchTest.class))
                .filters(TagFilter.includeTags("exception"))
                .build();

        launcher.execute(request,summaryGeneratingListener);
        try(var writer = new PrintWriter(System.out)){
            summaryGeneratingListener.getSummary().printTo(writer);
        }

    }
}