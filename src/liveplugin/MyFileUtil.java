package liveplugin;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;


public class MyFileUtil {
	public static List<File> listFilesIn(File folder) {
		File[] files = folder.listFiles();
		if (files == null) return emptyList();
		else return Arrays.asList(files);
	}

	public static List<String> fileNamesMatching(String regexp, String path) {
		return fileNamesMatching(regexp, new File(path));
	}

	public static List<String> fileNamesMatching(String regexp, File path) {
		List<File> files = FileUtil.findFilesByMask(Pattern.compile(regexp), path);
		return ContainerUtil.map(files, File::getName);
	}

	public static String asUrl(File file) {
		try {
			return file.toURI().toURL().toString();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Nullable public static File findScriptFileIn(final String path, String fileName) {
		List<File> result = findScriptFilesIn(path, fileName);
		if (result.size() == 0) {
			return null;
		} else if (result.size() == 1) {
			return result.get(0);
		} else {
			List<String> filePaths = ContainerUtil.map(result, File::getAbsolutePath);
			throw new IllegalStateException("Found several scripts files under " + path + ":\n" + StringUtil.join(filePaths, ";\n"));
		}
	}

	public static List<File> findScriptFilesIn(final String path, String fileName) {
		File rootScriptFile = new File(path + File.separator + fileName);
		if (rootScriptFile.exists()) return asList(rootScriptFile);

		List<File> result = new ArrayList<>();
		for (File file : allFilesInDirectory(new File(path))) {
			if (fileName.equals(file.getName())) {
				result.add(file);
			}
		}
		return result;
	}

	private static List<File> allFilesInDirectory(File dir) {
		LinkedList<File> result = new LinkedList<>();
		File[] files = dir.listFiles();
		if (files == null) return result;

		for (File file : files) {
			if (file.isFile()) {
				result.add(file);
			} else if (file.isDirectory()) {
				result.addAll(allFilesInDirectory(file));
			}
		}
		return result;
	}

	public static String[] readLines(String url) throws IOException {
		return FileUtil.loadTextAndClose(new BufferedReader(new InputStreamReader(new URL(url).openStream()))).split("\n");
	}
}
