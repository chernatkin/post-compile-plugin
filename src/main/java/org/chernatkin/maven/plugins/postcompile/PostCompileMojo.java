package org.chernatkin.maven.plugins.postcompile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "postcompile",
      defaultPhase = LifecyclePhase.COMPILE,
      threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PostCompileMojo extends AbstractMojo {

    private static final URL[] EMPTY_URL_ARRAY = new URL[0];

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(readonly = true, required = true)
    private String[] executionClasses;

    @Parameter(readonly = true, required = false)
    private String[] additionalResources;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        List<URL> urls = null;
        URLClassLoader cl = null;
        String className = null;
        try {
            urls = getCompileClasspathElements(getProject());
            logDebug("Found class path urls:", urls);

            cl = new URLClassLoader(urls.toArray(EMPTY_URL_ARRAY), ClassLoader.getSystemClassLoader().getParent());

            final List<String> execClassNames = Stream.of(Optional.ofNullable(getExecutionClasses()).orElse(ArrayUtils.EMPTY_STRING_ARRAY))
                                                      .map(String::trim)
                                                      .filter(StringUtils::isNotBlank)
                                                      .collect(Collectors.toList());

            if (execClassNames.isEmpty()) {
                throw new IllegalArgumentException("Execution classes are not configured:" + getExecutionClasses());
            }

            for (final String execClassName : execClassNames) {
                className = execClassName;
                executeClass(cl, execClassName);
            }

        } catch (MalformedURLException mfue) {
            throw new MojoExecutionException("Class path is invalid:" + urls, mfue);
        } catch (ClassNotFoundException cnfe) {
            throw new MojoExecutionException("Execution class name is invalid:" + className, cnfe);
        } catch (IllegalAccessException iae) {
            throw new MojoExecutionException("Not accessible method run() or constructor without params of class:" + className, iae);
        } catch (InstantiationException inste) {
            throw new MojoExecutionException("Can`t initialize class:" + className, inste);
        } catch (Exception e) {
            throw new MojoFailureException("Failed execution class:" + className, e);
        } finally {
            if (cl != null) {
                try {
                    cl.close();
                } catch (IOException e) {
                    getLog().error(e);
                }
            }
        }
    }

    private List<URL> getCompileClasspathElements(MavenProject project) throws MalformedURLException {
        final List<URL> list = new ArrayList<>(project.getArtifacts().size() + 1);

        list.add(new File(project.getBuild().getOutputDirectory()).toURI().toURL());

        for (Artifact artifact : project.getArtifacts()) {
            list.add(artifact.getFile().toURI().toURL());
        }

        logDebug("Additional resources:", getAdditionalResources());
        for (String resource : Optional.ofNullable(getAdditionalResources()).orElse(ArrayUtils.EMPTY_STRING_ARRAY)) {
            list.add(new URL(resource));
        }

        return list;
    }

    protected void executeClass(final ClassLoader cl, final String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        final Class<? extends Runnable> execClass = (Class<? extends Runnable>) cl.loadClass(className);
        logDebug("Loaded execution class:", execClass);

        if (!Runnable.class.isAssignableFrom(execClass)) {
            throw new IllegalArgumentException("Execution class should be instance of java.lang.Runable:" + execClass);
        }
        
        execClass.newInstance().run();
        logDebug("Completed run() method");
    }

    protected void logDebug(String msg, Object... args) {
        if (!getLog().isDebugEnabled()) {
            return;
        }

        CharSequence logMessage = msg;
        if (!ArrayUtils.isEmpty(args)) {
            logMessage = new StringBuilder(msg)
                .append(Stream.of(args)
                              .map(Objects::toString)
                              .collect(Collectors.joining(", ")));
        }

        getLog().debug(logMessage);
    }

    protected MavenProject getProject() {
        return project;
    }

    protected String[] getExecutionClasses() {
        return executionClasses;
    }

    protected String[] getAdditionalResources() {
        return additionalResources;
    }
}
