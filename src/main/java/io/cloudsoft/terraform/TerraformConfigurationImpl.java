package io.cloudsoft.terraform;

import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.feed.ssh.SshPollValue;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Function;

public class TerraformConfigurationImpl extends SoftwareProcessImpl implements TerraformConfiguration {

    private SshFeed sshFeed;

    @Override
    public void init() {
        super.init();
        checkConfiguration();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();

        Maybe<SshMachineLocation> machine = Locations.findUniqueSshMachineLocation(getLocations());
        if (machine.isPresent()) {
            sshFeed = SshFeed.builder()
                    .entity(this)
                    .period(Duration.TEN_SECONDS)
                    .machine(machine.get())
                    .poll(new SshPollConfig<String>(SHOW)
                            .command(getDriver().makeTerraformCommand("show"))
                            .onSuccess(new Function<SshPollValue, String>() {
                                @Override
                                public String apply(SshPollValue input) {
                                    return input.getStdout();
                                }})
                            .onFailure(new Function<SshPollValue, String>() {
                                @Override
                                public String apply(SshPollValue input) {
                                    ServiceStateLogic.setExpectedState(TerraformConfigurationImpl.this, Lifecycle.ON_FIRE);
                                    return input.getStderr();
                                }}))
//                    .poll(new SshPollConfig<String>(STATE)
//                            .command(getDriver().makeTerraformCommand("state"))
//                            .onSuccess(new Function<SshPollValue, String>() {
//                                @Override
//                                public String apply(SshPollValue input) {
//                                    return input.getStdout();
//                                }})
//                            .onFailure(new Function<SshPollValue, String>() {
//                                @Override
//                                public String apply(SshPollValue input) {
//                                    ServiceStateLogic.setExpectedState(TerraformConfigurationImpl.this, Lifecycle.ON_FIRE);
//                                    return input.getStderr();
//                                }}))
                    .poll(new SshPollConfig<String>(PLAN)
                            .command(getDriver().makeTerraformCommand("plan"))
                            .onSuccess(new Function<SshPollValue, String>() {
                                @Override
                                public String apply(SshPollValue input) {
                                    return input.getStdout();
                                }})
                            .onFailure(new Function<SshPollValue, String>() {
                                @Override
                                public String apply(SshPollValue input) {
                                    ServiceStateLogic.setExpectedState(TerraformConfigurationImpl.this, Lifecycle.ON_FIRE);
                                    return input.getStderr();
                                }}))
                    .build();
        }
    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
        if (sshFeed != null) sshFeed.stop();
        super.disconnectSensors();
    }

    private void checkConfiguration() {
        String configurationUrl = getConfig(CONFIGURATION_URL);
        String configurationContents = getConfig(CONFIGURATION_CONTENTS);

        // Exactly one of the two must have a value
        if (Strings.isBlank(configurationUrl) == Strings.isBlank(configurationContents))
            throw new IllegalArgumentException("Exactly one of the two must have a value: '"
                    + CONFIGURATION_URL.getName() + "' or '" + CONFIGURATION_CONTENTS.getName() + "'.");
    }

    @Override
    public Class<?> getDriverInterface() {
        return TerraformDriver.class;
    }

    @Override
    public TerraformDriver getDriver() {
        return (TerraformDriver) super.getDriver();
    }
}
