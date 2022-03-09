package com.playmonumenta.networkchat.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.stream.Stream;

public class FileUtils {
	/**
	 * Returns a list of all files in the directory that are both regular files
	 * AND end with the specified string
	 */
	public static ArrayList<File> getFilesInDirectory(String folderPath,
	                                                  String endsWith) throws IOException {
		ArrayList<File> matchedFiles = new ArrayList<File>();

		try (Stream<Path> stream = Files.walk(Paths.get(folderPath), 100, FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(path -> {
				if (path.toString().toLowerCase().endsWith(endsWith)) {
					// Note - this will pass directories that end with .json back to the caller too
					matchedFiles.add(path.toFile());
				}
			});
		}

		return matchedFiles;
	}
}
