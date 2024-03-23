import java.lang.Boolean.getBoolean

// release version is used when building with -Drelease=true
val releaseVersion = "3.7.15"
val developmentVersion = "3.7.15.1-SNAPSHOT"

version = if( getBoolean( "release" ) ) releaseVersion else developmentVersion

plugins {
	`java-library`
	`maven-publish`
	signing
}

// check required Java version
if( JavaVersion.current() < JavaVersion.VERSION_1_8 )
	throw RuntimeException( "Java 8 or later required (running ${System.getProperty( "java.version" )})" )

// log version, Gradle and Java versions
println()
println( "-------------------------------------------------------------------------------" )
println( "JIDE Version: $version" )
println( "Gradle ${gradle.gradleVersion} at ${gradle.gradleHomeDir}" )
println( "Java ${System.getProperty( "java.version" )}" )
println()

java {
	toolchain {
		languageVersion.set( JavaLanguageVersion.of( 8 ) )
	}

	withSourcesJar()
	withJavadocJar()
}

sourceSets {
	// main code compiled with Java 8
	main {
		java.setSrcDirs( listOf( "src", "src-jdk8", "src-apple" ) )
		resources.setSrcDirs( listOf( "src" ) )

		java.include( "**/*.java" )
		resources.exclude( "**/*.java", "**/*.psd", "**/SwingWorker_COPYING" )
	}

	// Java 9+ sepecific code compiled with Java 9+
	create( "java9" ) {
		java {
			setSrcDirs( listOf( "src-jdk9" ) )
		}
	}
}

dependencies {
	add( "java9Compile", sourceSets.main.get().output )
}

tasks.withType<JavaCompile>().configureEach {
	sourceCompatibility = "1.8"
	targetCompatibility = "1.8"

	options.encoding = "ISO-8859-1"
}

tasks.named<JavaCompile>( "compileJava9Java" ) {
	javaCompiler.set( javaToolchains.compilerFor {
		languageVersion.set( JavaLanguageVersion.of( 9 ) )
	} )

	sourceCompatibility = "9"
	targetCompatibility = "9"

	options.compilerArgs.addAll( listOf(
		"--add-exports", "java.base/sun.security.action=ALL-UNNAMED",
		"--add-exports", "java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED",
		"--add-exports", "java.desktop/sun.awt.windows=ALL-UNNAMED",
		"--add-exports", "java.desktop/sun.awt.image=ALL-UNNAMED",
		"--add-exports", "java.desktop/sun.swing=ALL-UNNAMED"
	) )
}

tasks.jar {
	manifest.attributes(
		"Multi-Release" to "true",
		"Implementation-Version" to project.version
	)

	exclude( "apple/**", "com/apple/**" )

	into( "META-INF/versions/9" ) {
		from( sourceSets["java9"].output )
	}
}

tasks.named<Jar>( "sourcesJar" ) {
	exclude( "apple/**", "com/apple/**", "README.txt" )
}

tasks.javadoc {
	options {
		this as StandardJavadocDocletOptions
		use( true )
		addStringOption( "Xdoclint:none", "-Xdoclint:none" )
	}
	isFailOnError = false
}


publishing {
	publications {
		create<MavenPublication>( "maven" ) {
			artifactId = "jide-oss"
			groupId = "com.formdev"

			from( components["java"] )

			pom {
				name.set( "JIDE Common Layer" )
				description.set( "JIDE Common Layer (Professional Swing Components)" )
				url.set( "https://github.com/JFormDesigner/jide-oss" )

				licenses {
					license {
						name.set( "GPL with classpath exception" )
						url.set( "http://www.gnu.org/licenses/gpl.txt" )
					}
					license {
						name.set( "Free commercial license" )
						url.set( "http://www.jidesoft.com/purchase/SLA.htm" )
					}
				}

				developers {
					developer {
						id.set( "jidesoft" )
						name.set( "jidesoft" )
						email.set( "support@jidesoft.com" )
					}
				}

				scm {
					connection.set( "scm:git:git://github.com/JFormDesigner/jide-oss.git" )
					url.set( "https://github.com/JFormDesigner/jide-oss" )
				}
			}
		}
	}

	repositories {
		maven {
			name = "OSSRH"

			val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
			val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
			url = uri( if( getBoolean( "release" ) ) releasesRepoUrl else snapshotsRepoUrl )

			credentials {
				// get from gradle.properties
				val ossrhUsername: String? by project
				val ossrhPassword: String? by project

				username = System.getenv( "OSSRH_USERNAME" ) ?: ossrhUsername
				password = System.getenv( "OSSRH_PASSWORD" ) ?: ossrhPassword
			}
		}
	}
}

signing {
	// get from gradle.properties
	val signingKey: String? by project
	val signingPassword: String? by project

	val key = System.getenv( "SIGNING_KEY" ) ?: signingKey
	val password = System.getenv( "SIGNING_PASSWORD" ) ?: signingPassword

	useInMemoryPgpKeys( key, password )
	sign( publishing.publications["maven"] )
}

// disable signing of snapshots
tasks.withType<Sign>().configureEach {
	onlyIf { getBoolean( "release" ) }
}
