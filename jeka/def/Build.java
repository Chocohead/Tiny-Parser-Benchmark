import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.java.project.JkJavaProjectMaker;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

public class Build extends JkCommands {
	public final JkPluginJava javaPlugin = getPlugin(JkPluginJava.class);

	public static void main(String[] args) {
	    JkInit.instanceOf(Build.class, args).build();
    }

    @Override
    protected void setup() {
        JkJavaProject project = javaPlugin.getProject();
        project.setSourceVersion(JkJavaVersion.V8)
				.addDependencies(JkDependencySet.of()
						.and("org.openjdk.jmh:jmh-core:1.23")
						.and("org.openjdk.jmh:jmh-generator-annprocess:1.23")
						.and("com.google.guava:guava:28.2-jre") //Tiny-Mappings-Parser needs Guava but doesn't put it in the pom
						.and("net.fabricmc:tiny-mappings-parser:0.2.1.13")
						.and("org.ow2.asm:asm:7.1")
						.and("org.ow2.asm:asm-analysis:7.1")
						.and("org.ow2.asm:asm-commons:7.1")
						.and("org.ow2.asm:asm-tree:7.1")
						.and("org.ow2.asm:asm-util:7.1"));

        project.setManifest(project.getManifest().addMainClass("org.openjdk.jmh.Main"));
        
        JkJavaProjectMaker maker = project.getMaker();
        maker.addDownloadRepo(JkRepo.of("https://maven.fabricmc.net"));
        maker.defineMainArtifactAsFatJar(false);
    }

    @JkDoc("Clears the output then builds a fresh jar")
    public void build() {
    	javaPlugin.clean().pack();
    }
}