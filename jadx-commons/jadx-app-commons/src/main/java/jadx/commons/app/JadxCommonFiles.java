package jadx.commons.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import dev.dirs.ProjectDirectories;

public class JadxCommonFiles {

	private static final Path CONFIG_DIR;
	private static final Path CACHE_DIR;

	public static Path getConfigDir() {
		return CONFIG_DIR;
	}

	public static Path getCacheDir() {
		return CACHE_DIR;
	}

	static {
		DirsLoader loader = new DirsLoader();
		loader.init();
		CONFIG_DIR = loader.getConfigDir();
		CACHE_DIR = loader.getCacheDir();
	}

	private static final class DirsLoader {
		private @Nullable ProjectDirectories dirs;
		private Path configDir;
		private Path cacheDir;

		public void init() {
			try {
				configDir = loadEnvDir("JADX_CONFIG_DIR", pd -> pd.configDir);
				cacheDir = loadEnvDir("JADX_CACHE_DIR", pd -> pd.cacheDir);
			} catch (Exception e) {
				throw new RuntimeException("Failed to init common directories", e);
			}
		}

		private Path loadEnvDir(String envVar, Function<ProjectDirectories, String> dirFunc) throws IOException {
			String envDir = JadxCommonEnv.get(envVar, null);
			String dirStr;
			if (envDir != null) {
				dirStr = envDir;
			} else {
				dirStr = dirFunc.apply(loadDirs());
			}
			Path path = Path.of(dirStr).toAbsolutePath();
			Files.createDirectories(path);
			return path;
		}

		private synchronized ProjectDirectories loadDirs() {
			if (dirs == null) {
				dirs = ProjectDirectories.from("io.github", "skylot", "jadx");
			}
			return dirs;
		}

		public Path getCacheDir() {
			return cacheDir;
		}

		public Path getConfigDir() {
			return configDir;
		}
	}
}
