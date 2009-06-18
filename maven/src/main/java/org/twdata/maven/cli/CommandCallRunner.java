package org.twdata.maven.cli;

import java.io.File;
import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.embed.Embedder;

class CommandCallRunner {
    private final MavenSession session;
    private final MavenProject project;
    private final Log logger;
    private Embedder embedder;
    private Maven embeddedMaven;
    private File userDir;
    private boolean pluginExecutionOfflineMode;

    public CommandCallRunner(MavenSession session, MavenProject project,
            Log logger) throws MojoExecutionException {
        this.session = session;
        this.project = project;
        this.logger = logger;

        initEmbeddedMaven();
        resolvePluginExecutionOfflineMode();
    }

    private void initEmbeddedMaven() throws MojoExecutionException {
        try {
            embedder = new Embedder();
            embedder.start();
            embeddedMaven = (Maven) embedder.lookup(Maven.ROLE);
            userDir = new File(System.getProperty("user.dir"));
        } catch (PlexusContainerException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (ComponentLookupException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private void resolvePluginExecutionOfflineMode() {
        pluginExecutionOfflineMode = session.getSettings().isOffline();
    }

    public void executeCommand(CommandCall commandCall) {
        for (MavenProject currentProject : commandCall.getProjects()) {
            try {
                // QUESTION: which should it be?
                session.getExecutionProperties().putAll(commandCall.getProperties());
                //project.getProperties().putAll(commandCall.getProperties());

                session.setCurrentProject(currentProject);
                session.getSettings().setOffline(commandCall.isOffline() ? true : pluginExecutionOfflineMode);
                ProfileManager profileManager = new DefaultProfileManager(embedder.getContainer(),
                        commandCall.getProperties());
                profileManager.explicitlyActivate(commandCall.getProfiles());
                MavenExecutionRequest request = new DefaultMavenExecutionRequest(
                        session.getLocalRepository(), session.getSettings(),
                        session.getEventDispatcher(),
                        commandCall.getCommands(), userDir.getPath(),
                        profileManager, session.getExecutionProperties(),
                        project.getProperties(), true);
                if (!commandCall.isRecursive()) {
                    request.setRecursive(false);
                }
                request.setPomFile(new File(currentProject.getBasedir(),
                        "pom.xml").getPath());
                embeddedMaven.execute(request);
                logger.info("Current project: " + project.getArtifactId());
            } catch (Exception e) {
                logger.error(
                        "Failed to execute '" + commandCall.getCommands()
                                + "' on '" + currentProject.getArtifactId()
                                + "'");
            }
        }
    }
}
