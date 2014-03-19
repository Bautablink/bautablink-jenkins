package org.jenkinsci.plugins.bautablink;

import com.bautabits.bautabit.dto.PinConfiguration;
import hudson.Extension;
import hudson.Launcher;
import hudson.Plugin;
import hudson.XmlFile;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import com.bautabits.bautabit.Bautabit;
import com.bautabits.bautabit.dto.BautabitInfo;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BautablinkPlugin extends Plugin {

    public static final int NUM_THREADS = 2;

    public static final float BLINK_INTERVAL = 2f;
    public static final float BLINK_INTERVAL_OFF = 0.5f;

    protected transient ArrayList<Bautabit> bautabits = new ArrayList<Bautabit>();
    private transient ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

    private HashMap<String, Boolean> pinNameValues = new HashMap<String, Boolean>();
    private boolean blink;

    Logger log = Logger.getLogger(BautablinkPlugin.class.getName());

    public BautablinkPlugin() {
        pinNameValues.put("red", false);
        pinNameValues.put("green", false);
        pinNameValues.put("blue", false);
        pinNameValues.put("ok", false);
        pinNameValues.put("warning", false);
        pinNameValues.put("error", false);
    }

    public static enum Light {
        BLUE,
        YELLOW,
        RED
    }

    public static BautablinkPlugin getInstance() {
        return Hudson.getInstance().getPlugin(BautablinkPlugin.class);
    }

    public void initialize() {
        BautablinkWrapper.DescriptorImpl wrapperDescriptor = Hudson.getInstance().getDescriptorByType(BautablinkWrapper.DescriptorImpl.class);
        String hombitUrl = wrapperDescriptor.getUrl();
        if (hombitUrl != null && !hombitUrl.isEmpty()) {
            String[] urls = hombitUrl.split("\\s*,\\s*");
            bautabits.clear();
            for (String url : urls)
                bautabits.add(new Bautabit(url));
        } else {
            log.info("Starting bautabit discovery");
            try {
                bautabits.clear();
                bautabits.addAll(Bautabit.discover());
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to discover bautabit", e);
            }
        }
        if (bautabits != null && !bautabits.isEmpty()) {
            for (Bautabit bautabit : bautabits) {
                try {
                    BautabitInfo info = bautabit.fetchInfo();
                    log.info("Found " + info.getType() + " " + info.getId());
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Failed to initialize bautabit at " + hombitUrl, e);
                }
            }
        } else
            log.warning("No bautabit found");
    }

    public void lightsByBuildResults() {
        System.out.println("Lights by build results");
        int blueCount = 0, yellowCount = 0, redCount = 0, buildingCount = 0;
        List<TopLevelItem> items = Hudson.getInstance().getItems();
        for (TopLevelItem item : items) {
            if (item instanceof Project) {
                System.out.print("  " + item.getName() + ": ");
                Project project = (Project) item;
                if (project.getLastBuild() == null) {
                    System.out.println("no build");
                    continue;
                }
                if (project.isDisabled()) {
                    System.out.println("disabled");
                    continue;
                }
                Result result = null;
                if (project.getLastBuild().isBuilding()) {
                    buildingCount++;
                    if (project.getLastCompletedBuild() == null) {
                        System.out.println("no completed build");
                        continue;
                    } else
                        result = project.getLastCompletedBuild().getResult();
                } else
                    result = project.getLastBuild().getResult();
                if (result == null) {
                    System.out.println("no result");
                    continue;
                }

                boolean skipped = false;
                for (Object descriptor : project.getBuildWrappers().keySet()) {
                    if (descriptor instanceof BautablinkWrapper.DescriptorImpl) {
                        BautablinkWrapper wrapper = (BautablinkWrapper) project.getBuildWrappers().get(descriptor);
                        if (wrapper.getSkip())
                            skipped = true;
                        if (wrapper.getSkipUnstable() && (result.color == BallColor.YELLOW || result.color == BallColor.YELLOW_ANIME))
                            skipped = true;
                    }
                }
                if (skipped) {
                    System.out.println("skipped");
                    continue;
                }

                System.out.println(result + " " + result.color);
                switch (result.color) {
                    case BLUE_ANIME:
                    case BLUE:
                        blueCount++;
                        break;
                    case YELLOW_ANIME:
                    case YELLOW:
                        yellowCount++;
                        break;
                    case RED_ANIME:
                    case RED:
                        redCount++;
                        break;
                }
            }
        }
        System.out.println(blueCount + " blue, " + yellowCount + " yellow, " + redCount + " red, " + buildingCount + " building");
        blink = buildingCount > 0;
        if (redCount > 0) {
            light(Light.RED);
        } else if (yellowCount > 0) {
            light(Light.YELLOW);
        } else if (blueCount > 0) {
            light(Light.BLUE);
        } else {
            lightsOff();
        }
    }

    public void light(Light light) {
        System.out.println("Light " + light + (blink ? " blink" : ""));
        for (String pin : pinNameValues.keySet()) pinNameValues.put(pin, false);
        switch (light) {
            case BLUE:
                pinNameValues.put("blue", true);
                pinNameValues.put("ok", true);
                break;
            case YELLOW:
                pinNameValues.put("red", true);
                pinNameValues.put("green", true);
                pinNameValues.put("warning", true);
                break;
            case RED:
                pinNameValues.put("red", true);
                pinNameValues.put("error", true);
                break;
        }
        setLights();
    }

    public void lightsOff() {
        System.out.println("Lights off");
        blink = false;
        for (String pin : pinNameValues.keySet()) pinNameValues.put(pin, false);
        setLights();
    }

    public void lightsOn() {
        System.out.println("Lights on");
        blink = false;
        for (String pin : pinNameValues.keySet()) pinNameValues.put(pin, true);
        setLights();
    }

    private void setLights() {
        for (final Bautabit bautabit : bautabits) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        HashMap<String, PinConfiguration> config = new HashMap<String, PinConfiguration>();
                        for (String pinName : pinNameValues.keySet()) {
                            if (blink && pinNameValues.get(pinName))
                                config.put(pinName, PinConfiguration.blink(BLINK_INTERVAL, BLINK_INTERVAL_OFF));
                            else
                                config.put(pinName, PinConfiguration.out());
                        }
                        bautabit.configureNamedPins(config);
                        bautabit.setPins(pinNameValues);
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Failed to set lights", e);
                    }
                }
            });
        }

    }

    @Override
    public void start() throws Exception {
        System.out.println("BLINK start");
    }

    @Override
    public void postInitialize() throws Exception {
        System.out.println("BLINK postInitialize");
        System.out.println("foo");
        initialize();
        System.out.println("bar");
        lightsOn();
        System.out.println("BLINK postInitialize done");
    }

    @Override
    public void stop() throws Exception {
        System.out.println("BLINK stop");
        lightsOff();
    }

    @Extension
    public static class BlinkItemListener extends ItemListener {
        @Override
        public void onLoaded() {
            System.out.println("BLINK onLoaded");
            if (BautablinkPlugin.getInstance() != null)
                BautablinkPlugin.getInstance().lightsByBuildResults();
        }

        @Override
        public void onUpdated(Item item) {
            System.out.println("BLINK onUpdated " + item.getName());
        }

        @Override
        public void onBeforeShutdown() {
            System.out.println("BLINK onBeforeShutdown");
            if (BautablinkPlugin.getInstance() != null)
                BautablinkPlugin.getInstance().lightsOff();
        }
    }

    @Extension
    public static class BlinkSaveableListener extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            System.out.println("BLINK onChange " + o.getClass().getSimpleName());
            if (o instanceof Project) {
                // React to disabled/enabled projects
                BautablinkPlugin.getInstance().lightsByBuildResults();
            } else if (o instanceof BautablinkWrapper) {
                BautablinkPlugin.getInstance().initialize();
            }
        }
    }

    @Extension
    public static class BlinkRunListener extends RunListener<AbstractBuild> {
        @Override
        public void onCompleted(AbstractBuild abstractBuild, @Nonnull TaskListener listener) {
            System.out.println("BLINK onCompleted " + abstractBuild.getProject().getName() + " " + abstractBuild.getResult());
            BautablinkPlugin.getInstance().lightsByBuildResults();
        }

        @Override
        public void onStarted(AbstractBuild abstractBuild, TaskListener listener) {
            System.out.println("BLINK onStarted " + abstractBuild.getProject().getName() + " #" + abstractBuild.getNumber());
            BautablinkPlugin.getInstance().lightsByBuildResults();
        }

        @Override
        public void onDeleted(AbstractBuild abstractBuild) {
            System.out.println("BLINK onDeleted " + abstractBuild.getProject().getName());
            BautablinkPlugin.getInstance().lightsByBuildResults();
        }

        @Override
        public void onFinalized(AbstractBuild abstractBuild) {
            //System.out.println("BLINK onFinalized " + abstractBuild.getProject().getName());
        }

        @Override
        public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
            System.out.println("BLINK setupEnvironment " + build.getProject().getName());
            return super.setUpEnvironment(build, launcher, listener);    //To change body of overridden methods use File | Settings | File Templates.
        }
    }

    @Extension
    public static class BlinkChecker extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return 30000;
        }

        @Override
        protected void doRun() throws Exception {
            System.out.println("BLINK check");
            BautablinkPlugin.getInstance().setLights();
        }
    }

}

