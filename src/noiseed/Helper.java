package noiseed;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TreeSet;

// HELPER FUNCTIONS
public class Helper {
    	
	/**
	 * Convenience function to concatenate file name and format.
	 * 
	 * @param fileName the name of the file
	 * @param extension the format of the file
	 * @return the concatenated file name String
	 */
	public static String setFileName(String fileName, String extension) {
		return fileName + "." + extension;
	}

	/**
	 * Clamps the given value within the given bounds.
	 * <p>
	 * From {@link https://stackoverflow.com/a/22757471}.
	 * 
	 * @param value a value
	 * @param min lower bound
	 * @param max upper bound
	 * @return the clamped value between {@code min} and {@code max}
	 */
	public static int clamp(int value, int min, int max) {
		return Math.min(Math.max(value, min), max);
	}

	/**
	 * Convert a {@code LocalDateTime} object to {@code String} of supplied format pattern.
	 * 
	 * @param date the value to be formatted
	 * @param pattern the {@code String} pattern for the formatter
	 * @return the formatted date as a {@code String}
	 */
	public static String dateTimeToString(LocalDateTime date, String pattern) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
		return date.format(formatter);
	}

	/**
	 * Default version of {@code dateTimeToString(LocalDateTime, String)}.
	 * 
	 * @return {@code LocalDateTime.now()} formatted as {@code "yyyy-MM-dd_HH-mm-ss-nnnnnnnnn"}
	 */
	public static String dateTimeToString() {
		return dateTimeToString(LocalDateTime.now(), "yyyy-MM-dd_HH-mm-ss-nnnnnnnnn");
	}

	/**
	 * Get the index position of a {@code String} matching an array entry.
	 * 
	 * @param searchString a {@code String} to be matched
	 * @param stringArray an array to look in
	 * @return index of the array at which the {@code String} matches or 0
	 */
	public static int getStringArrayIndex(String searchString, String[] stringArray) {
		for (int i = 0; i < stringArray.length; i++) {
			if (stringArray[i] == searchString) {
				return i;
			}
		}
		// Returns 0 as a fallback value
		return 0;
	}
	
	/**
	 * Create a description for the "Files of Type" box in a save dialog.
	 * 
	 * @param fileType a term describing the general file type
	 * @param formats array of file formats
	 * @return a String containing the description for a save dialog
	 */
	public static String getFileFormatFilterDescription(String fileType, String[] formats) {
		// TreeSet to get alphabetical order and eliminate duplicates
		Set<String> formatSet = new TreeSet<>();
		for (String ext : formats) {
			formatSet.add("*." + ext.toLowerCase());
		}
		String extensions = String.join("; ", formatSet);
		return fileType + " (" + extensions + ")";
	}
}
