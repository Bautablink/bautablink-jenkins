package org.jenkinsci.plugins.bautablink;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.tasks.*;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import com.bautabits.bautabit.Bautabit;
import com.bautabits.bautabit.dto.BautabitInfo;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public class BautablinkWrapper extends BuildWrapper implements Serializable {

    boolean skip;
    boolean skipUnstable;

    @DataBoundConstructor
    public BautablinkWrapper(boolean skip, boolean skipUnstable) {
        System.out.println("WRAPPER constructor: skip=" + skip + ", skipUnstable=" + skipUnstable);
        this.skip = skip;
        this.skipUnstable = skipUnstable;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        System.out.println("WRAPPER setUp");
        return new Environment() {

        };
    }

    public boolean getSkip() {
        return this.skip;
    }

    public boolean getSkipUnstable() {
        return skipUnstable;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        private String url;

        public DescriptorImpl() {
            super(BautablinkWrapper.class);
            load();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> abstractProject) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Custom Bautablink rules";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            System.out.println("WRAPPER configure");
            JSONObject configuration = json.getJSONObject("bautablink");
            this.url = configuration.getString("url");
            save();
            BautablinkPlugin.getInstance().initialize();
            return super.configure(req, json);
        }

        public FormValidation doCheckUrl(@QueryParameter String value)
                throws IOException, ServletException {
            System.out.println("WRAPPER doCheckUrl '" + value + "'");
            if (value == null || value.isEmpty())
                return FormValidation.ok("Using auto discovery");
            else {
                String[] urls = value.split("\\s*,\\s*");
                String infoText = "";
                boolean error = false;
                for (String url : urls) {
                    Bautabit bautabit = new Bautabit(url);
                    try {
                        BautabitInfo info = bautabit.fetchInfo();
                        infoText += (urls.length > 1 ? url + ": " : "") + info.getType() + " " + info.getId() + "\n";
                    } catch (Exception e) {
                        infoText += (urls.length > 1 ? url + ": " : "") + e.getMessage() + "\n";
                        error = true;
                    }
                }
                if (error)
                    return FormValidation.error(infoText);
                else
                    return FormValidation.ok(infoText);
            }
        }

        public FormValidation doTestConnection(@QueryParameter("url") String url) {
            System.out.println("WRAPPER doTestConnection " + url);
            try {
                if (url != null && url.length() > 0) {
                    doCheckUrl(url);
                    return FormValidation.ok("");
                }
                String infoText = "";
                boolean error = false;
                List<Bautabit> bautabits = Bautabit.discover();
                for (Bautabit bautabit : bautabits) {
                    try {
                        BautabitInfo info = bautabit.fetchInfo();
                        infoText += info.getType() + " " + info.getId() + "\n";
                    } catch (Exception e) {
                        infoText += e.getMessage() + "\n";
                        error = true;
                    }
                }
                if (error)
                    return FormValidation.warning(infoText);
                else if (bautabits.isEmpty())
                    return FormValidation.warning("No bautabit found");
                else
                    return FormValidation.ok(infoText);
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error(e.getLocalizedMessage());
            }
        }

        public String getUrl() {
            return url;
        }

    }

    @Extension
    public static class BlinkItemListener extends ItemListener {
        @Override
        public void onLoaded() {
            System.out.println("WRAPPER loaded");
            DescriptorImpl descriptor = Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
            System.out.println("  url = " + descriptor.getUrl());
        }
    }
}
