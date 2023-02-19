package noiseed;

import java.awt.Color;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

public class Noiseed {
	
	// JSON keys
	public static final String SEEDKEY = "seed";
	public static final String RULEKEY = "rules";
	// Default image format
	public static final String DEFAULT_IMAGE_FORMAT = "png";

	// Get an array of available formats
	public static String[] availableFormats = ImageIO.getWriterFormatNames();
	
	// First row of the image containing 0s and 1s
	private static byte[] seed;
	// 2-dimensional array representing the image containing 0s and 1s
	private static byte[][] rowlist;
	// Array of RGB values
	private static int[] rgbArray;
	// Width and height of the image that is to be generated
	// Width and height both need to be > 0
	private static int width = 512;
	private static int height = 512;
	// Rule complexity (2^n rule entries)
	// n >= 0 
	// For example n = 0 = 2^0 = 1 = There is a single rule which is applied to all entries
	private static int n = 5;
	// (1 << n) is equivalent to 2**n | 2^n | two to the power of n
	private static HashMap<Integer, Byte> rules = new HashMap<Integer, Byte>(1 << n);

	// Set up 2 colors to replace the values 1 (colorOne) and 0 (colorZero) in rowlist
	private static int colorOne = Color.WHITE.getRGB();
	private static int colorZero = Color.BLACK.getRGB();
	// Holds the generated image
	private static BufferedImage img;

	// Represents percentage (0 - 100)
	private static int generationProgress = 0;
	// Used to estimate and calculate generationProgress
	private static long currentTotal = 0;
	private static long maxTotal	 = 0;
	// Flag that can be set externally via enableCalculateProgress()
	private static boolean calculateProgress = false;
	// Initialize estimates for progress calculation
	private static long seedCost = 0;
	private static long ruleCost = 0;
	private static long rowlistCost = Long.MAX_VALUE;
	private static long rgbArrayCost = 0;
	private static long imageCreationEstimate = 0;

	// rng used for seed and rule generation
	private static Random rand = new Random();

	// MAIN FUNCTION

	// 						args[ amount 	width 	height 	n 	format	infoFile ]
	// ---------------------------------------------------------------------------
	// Example call: java Noiseed 0 		1920 	1080 	5 	png		info.json

	/**
	 * Generates {@code amount} of images of type {@code format}.
	 * When all optional parameters are passed:
	 * Generates "amount" of images of size {@code width} x {@code height} with rule complexity {@code n} given a {@code seed} and/or {@code rules} from {@code infoFile}.
	 * 
	 * @param args an array of optional parameters:
	 * 			<p>
	 * 			{@code args[0]} amount of images to be generated, 0 for infinite
	 * 			<p>
	 * 			{@code args[1]}	width of image
	 * 			<p>
	 * 			{@code args[2]} height of image
	 *			<p>
	 * 			{@code args[3]} n rule complexity of rules
	 * 			<p>
	 * 			{@code args[4]} format for saved images
	 * 			<p>
	 * 			{@code args[5]} infoFile containing seed and/or rules for image generation
	 */
	public static void main(String[] args) {

		// Indices for args[]
		final int INDEX_AMOUNT 		= 0;
		final int INDEX_WIDTH 		= 1;
		final int INDEX_HEIGHT 		= 2;
		final int INDEX_N 			= 3;
		final int INDEX_FORMAT 		= 4;
		final int INDEX_INFOFILE 	= 5;
		// Max args count
		final int MAX_ARGS 			= 6;

		// Default value needs to be greater than 0
		int amountToBeGenerated = 1;
		// Default value leads to endless while loop
		boolean decrementAmount = false;
		// Default value leads to 
		// keepCurrentSeed = false
		// keepCurrentRules = false
		int statusCodeInfoFile = 0;
		// Default image format
		String format = DEFAULT_IMAGE_FORMAT;

		// 1-5 args are valid, unsupplied args are replaced by default values
		if (args.length > 0 && args.length <= MAX_ARGS) {
			// Amount of images to be generated
			// 0 (or smaller) for unlimited images (until stopping the program or error)
			int amount = Integer.parseInt(args[INDEX_AMOUNT]);
			if (amount > 0) {
				amountToBeGenerated = amount;
				decrementAmount = true;
			}
			// Width of image
			if (args.length > INDEX_WIDTH) {
				width = Integer.parseInt(args[INDEX_WIDTH]);
			}
			// Height of image
			if (args.length > INDEX_HEIGHT) {
				height = Integer.parseInt(args[INDEX_HEIGHT]);
			}
			// Rule complexity of rules (2^n rules)
			if (args.length > INDEX_N) {
				n = Integer.parseInt(args[INDEX_N]);
			}
			// Set format
			if (args.length > INDEX_FORMAT) {
				// Check if entered format is available
				if (Arrays.stream(Noiseed.availableFormats).anyMatch(format::equalsIgnoreCase)) {
					format = args[INDEX_FORMAT];
				}
			}
			// JSON file from which to load seed and rules
			if (args.length > INDEX_INFOFILE) {
				String infoFileName = args[INDEX_INFOFILE];
				// Sets seed and rules if present in JSON file
				statusCodeInfoFile = setFromJSON(infoFileName, true, true);
			}
		}

		// Generate images infinitely or according to amount
		while (amountToBeGenerated > 0) {
			// true for statusCode 1 or 3
			boolean keepCurrentSeed = keepSeedFromStatusCode(statusCodeInfoFile);
			// true for statusCode 2 or 3
			boolean keepCurrentRules = keepRulesFromStatusCode(statusCodeInfoFile);
			// Generate the image
			generateImage(width, height, n, keepCurrentSeed, keepCurrentRules);
			
			// Generate a fileName
			String fileName = Helper.dateTimeToString();
			String fullImageFileName = Helper.setFileName(fileName, format);
			
			// Check if a file with fileName already exists
			Path path = Path.of(fullImageFileName);
			boolean fileExists = Files.exists(path);

			// Limit retries for following file existence checking
			int retries = 5;
			// If a file already exists regenerate a fileName and check whether that one is unused
			while (fileExists) {
				// Abort program after exhausting all retries
				if (retries == 0) {
					System.out.println("Can not generate unused file name");
					System.out.println("Last try was: " + fullImageFileName);
					return;
				}
				fileName = Helper.dateTimeToString();
				fullImageFileName = Helper.setFileName(fileName, format);
				path = Path.of(fullImageFileName);
				fileExists = Files.exists(path);
				--retries;
			}
			// Save generated image
			boolean imageSaved = saveImage(fileName, format);
			if (!imageSaved) {
				System.out.println("Could not save image file " + fullImageFileName);
			}
			// Save info file
			String infoFileName = fileName + "_info";
			boolean infoSaved = saveJSON(infoFileName, createInfoJSONObject());
			if (!infoSaved) {
				System.out.println("Could not save info file " + infoFileName + ".json");
			}
			// If an amount was supplied, decrement counter variable
			if (decrementAmount) {
				--amountToBeGenerated;
			}
		}
	}

	// MAIN FUNCTIONALITY

	/**
	 * Generate image and set it to {@code img}.
	 * 
	 * @param width sets the width for the generated image
	 * @param height sets the height for the generated image
	 * @param n sets the number of rules (2^n)
	 * @param keepCurrentSeed decide whether to keep the current {@code seed} or not
	 * @param keepCurrentRules decide whether to keep the current {@code rules} or not 
	 * 
	*/
	public static void generateImage(int width, int height, int n, boolean keepCurrentSeed, boolean keepCurrentRules) {
		// Check if progress tracking is desired
		if (calculateProgress) {
			// Reset progress to 0 (%)
			setGenerationProgress(0, 100);
			// Estimates for progress display
			currentTotal = 0;
			seedCost = keepCurrentSeed ? 0 : width;
			ruleCost = keepCurrentRules ? 0 : (1 << n);
			rowlistCost = (long) width * ((long) height - 1) * (long) n;
			rgbArrayCost = (long) width * (long) height;
			imageCreationEstimate = (long) width * (long) height;
			maxTotal = seedCost + ruleCost + rowlistCost + rgbArrayCost + imageCreationEstimate;
		}

		if (!keepCurrentSeed) {
			// currentTotal += width
			seed = createSeed(width); 
		}

		if (!keepCurrentRules) {
			// currentTotal += (1 << n)
			rules = createRules(n); 
		}

		// currentTotal += width * (height - 1)
		rowlist = createRowlist(seed, rules, width, height, n); 
		
		// currentTotal += width * height
		rgbArray = createRgbArray(rowlist); 

		// estimate: (width * height * 1) / 6
		setImg(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
		// Set estimated progress
		if (calculateProgress) {
			setGenerationProgress(seedCost + ruleCost + rowlistCost + rgbArrayCost + (imageCreationEstimate / 6), maxTotal);
		}

		// estimate: (width * height * 5) / 6
		getImg().setRGB(0, 0, width, height, rgbArray, 0, width);
		// Set progress to 100 (%)
		if (calculateProgress) {
			setGenerationProgress(100, 100);
		}
	}

	/**
	 * Generate a seed for image generation containing 0s and 1s.
	 * 
	 * @param width amount of entries to be generated
	 * @return byte array of size width containing 0s and 1s
	 */
	public static byte[] createSeed(int width) {
		byte[] newSeed = new byte[width];
		for (int i = 0; i < width; i++) {
			newSeed[i] = (byte) rand.nextInt(2);
			// keep track of "progress"
			if (calculateProgress) {
				setGenerationProgress(++currentTotal, maxTotal);
			}
		}
		return newSeed;
	}

	/**
	 * Generate a ruleset for image generation.
	 * 
	 * @param n rule complexity determining number of rules to be generated (2^n)
	 * @return {@code Hashmap} with 2^n rules, each rule represented by a Key (1 to 2^n) and Value (1 or 0)
	 */
	public static HashMap<Integer, Byte> createRules(int n) {
		HashMap<Integer, Byte> newRules = new HashMap<Integer, Byte>(1 << n);
		for (int i = 0; i < (1 << n); i++) {
			newRules.put(i, (byte) rand.nextInt(2));
			// keep track of "progress"
			if (calculateProgress) {
				setGenerationProgress(++currentTotal, maxTotal);
			}
		}
		return newRules;
	}

	/**
	 * Create a 2-D array for an image based on {@code seed} and {@code rules}, containing 0s and 1s.
	 * 
	 * @param seed sets the first row of the array
	 * @param rules sets the rules
	 * @param width sets the width of each generated row
	 * @param height sets the number of rows to be generated
	 * @param n determines the number of entries that are used from the previous row
	 * @return 2-D array representing the image containing 0s and 1s as entries
	 */
	public static byte[][] createRowlist(byte[] seed, HashMap<Integer, Byte> rules, int width, int height, int n) {
		byte[][] newRowlist = new byte[height][width];
		newRowlist[0] = seed;
		// i ==> row-index
		for (int i = 1; i < height; i++) {
			byte[] nextRow = new byte[width];
			// j ==> column-index of current row
			for (int j = 0; j < width; j++) {
				int ruleKey = 0;
				// k ==> position of [window] to calculate entry j
				// Example for n = 2
				// (row i-1) ... | ... | ... [ MSB | LSB ] ... | ...
				// (row i)   ... | j-2 | j-1 |  j  | j+1 | j+2 | ...
				// Example for n = 3
				// (row i-1) ... | ... [ MSB | BIT | LSB ] ... | ...
				// (row i)   ... | j-2 | j-1 |  j  | j+1 | j+2 | ...
				for (int k = 0; k < n; k++) {
					ruleKey += newRowlist[i - 1][Math.floorMod((n / 2) - k + j, width)] == 1 ? 1 << k : 0;
					// keep track of "progress"
					if (calculateProgress) {
						setGenerationProgress(++currentTotal, maxTotal);
					}
				}
				// Set the entry j according to calculated ruleKey
				nextRow[j] = rules.get(ruleKey);
			}
			// Add the newly generated row to newRowlist
			newRowlist[i] = nextRow;
		}
		return newRowlist;
	}

	/**
	 * Create an array with RGB values based on {@code rowlist}.
	 * 
	 * @param rowlist 2-dimensional byte array containing 0s and 1s
	 * @return int array with color values, used to create a BufferedImage
	 */
	public static int[] createRgbArray(byte[][] rowlist) {
		// Get sizes from rowlist (independent of global width and height)
		int rowWidth = rowlist[0].length;
		int colHeight = rowlist.length;
		// Set size
		rgbArray = new int[rowWidth * colHeight];
		// Fill rgbArray with color values according to rowlist
		for (int i = 0; i < rowWidth * colHeight; i++) {
			rgbArray[i] = rowlist[i / rowWidth][i % rowWidth] == 1 ? getColorOne() : getColorZero();
			// keep track of "progress"
			if (calculateProgress) {
				setGenerationProgress(++currentTotal, maxTotal);
			}
		}
		return rgbArray;
	}

	/**
	 * Change one color in {@code rgbArray} and {@code img}.
	 * 
	 * @param newColor the RGB value for the new color
	 * @param oldColor the RGB value for the old color
	 * @param changeColorOne determines which color is changed
	 */
	public static void changeColor(int newColor, int oldColor, boolean changeColorOne) {
		if (newColor == oldColor) {
			return;
		}
		// Get sizes from rowlist (independent of global width and height)
		int imageWidth = rowlist[0].length;
		// Control which color is changed
		int colorToChange = changeColorOne ? 1 : 0;
		for (int i = 0; i < rgbArray.length; i++) {
			// Only change 1s XOR 0s
			if (rowlist[i / imageWidth][i % imageWidth] == colorToChange) {
				// Adjust rgbArray
				rgbArray[i] = newColor;
				// Replace single RGB values in img
				getImg().setRGB(i % imageWidth, i / imageWidth, newColor);
			}
		}
	}
	
	/**
	 * Write {@code img} to file.
	 * 
	 * @param fileName the name of the file
	 * @param format desired format of the file
	 * @return false if an error occurred
	 */
	public static boolean saveImage(String fileName, String format) {
		File f = new File(Helper.setFileName(fileName, format));
		BufferedImage saveImage;
		// wbmp is supposed to be always supported
		if (format.equalsIgnoreCase("wbmp")) {
			// Get dimensions from rowlist
			int imageWidth = rowlist[0].length;
			int imageHeight = rowlist.length;
			// Need imageType to be TYPE_BYTE_BINARY
			BufferedImage oneBitImage = new BufferedImage(imageWidth, height, BufferedImage.TYPE_BYTE_BINARY);
			// Reconstruct the image
			oneBitImage.setRGB(0, 0, imageWidth, imageHeight, rgbArray, 0, imageWidth);
			// Assign the newly constructed image
			saveImage = oneBitImage;
		}
		else {
			// Assign the current image
			saveImage = getImg();
		}
		try {
			// Write saveImage to file f in format
			return ImageIO.write(saveImage, format.toLowerCase(), f);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Write {@code seed} and/or {@code rules} to file.
	 * 
	 * @param fileName the name of the file
	 * @param jsonobject the object to be saved
	 * @return {@code false} if an error occurred
	 */
	public static boolean saveJSON(String fileName, JSONObject jsonobject) {
		PrintWriter pw;
		try {
			// Set up the writer
			pw = new PrintWriter(Helper.setFileName(fileName, "json"), "UTF-8");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
			return false;
		}
		// Pass JSON as String to writer
		pw.println(jsonobject.toString(4));
		pw.close();
		return true;
	}
	
	/**
	 * Create a {@code JSONObject} from {@code seed} and/or {@code rules},
	 * <p>
	 * for example: {"seed": [0, 1], "rules": {"0": 0, "1": 0, "2": 1, "3": 0}}.
	 * 
	 * @return JSONObject containing the current {@code seed} and {@code rules}
	 */
	public static JSONObject createInfoJSONObject() {
		return new JSONObject().put("seed", new JSONArray(seed)).put("rules", new JSONObject(rules));
	}

	/**
	 * Sets current {@code seed} and {@code rules} according to provided JSON file.
	 * <p>
	 * Statuscodes:
	 * <p>
	 * 0 = neither {@code seed} + {@code rules} were set
	 * <p>
	 * 1 = only {@code seed} was set
	 * <p>
	 * 2 = only {@code rules} were set
	 * <p>
	 * 3 = {@code seed} + {@code rules} were set
	 * 
	 * @param fileName the name of the file
	 * @param setSeed enable setting of {@code seed} from the file
	 * @param setRules enable setting of {@code rules} from the file
	 * @return the appropriate status code
	 */
	public static int setFromJSON(String fileName, boolean setSeed, boolean setRules) {
		// Initialize return value
		int statusCode = 0;

		if (!setSeed && !setRules) {
			return 0;
		}

		String content;
		try {
			// File encoding UTF-8
			// Limit of roughly 2 GB filesize for readString
			content = Files.readString(Path.of(fileName), StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}
		JSONObject contentJSON = new JSONObject(content);

		// If setSeed, check that SEEDKEY exists in the file and associated content is not null
		if (setSeed && contentJSON.has(SEEDKEY) && !contentJSON.isNull(SEEDKEY)) {
			JSONArray seedJSON = contentJSON.getJSONArray(SEEDKEY);
			int len = seedJSON.length();
			if (len > 0) {
				byte[] newSeed = new byte[len];
				// Iterate over array entries of the file
				for (int i = 0; i < len; i++) {
					// Check that value is a Number
					if (seedJSON.get(i) instanceof Number) {
						byte value = seedJSON.getNumber(i).byteValue();
						// Check that only 1s and 0s are present
						if (value == 0 || value == 1) {
							newSeed[i] = value;
						} else {
							// Illegal value exists in the array
							return 0;
						}
					} else {
						// Associated value can not be converted
						return 0;
					}
				}
				// If this part is reached the newSeed is valid
				setWidth(len);
				seed = newSeed;
				statusCode += 1;
			} else {
				// Array is empty
				return 0;
			}
		}

		// If setRules check if RULEKEY exists and content is not null
		if (setRules && contentJSON.has(RULEKEY) && !contentJSON.isNull(RULEKEY)) {
			JSONObject rulesJSON = contentJSON.getJSONObject(RULEKEY);
			int len = rulesJSON.length();
			// Make sure entries sum up to a power of two
			// https://www.geeksforgeeks.org/java-program-to-find-whether-a-no-is-power-of-two/
			int ceil  = (int)(Math.ceil(Math.log(len) / Math.log(2)));
			int floor = (int)(Math.floor(Math.log(len) / Math.log(2)));
			if (len > 0 && floor == ceil) {
				HashMap<Integer, Byte> newRules = new HashMap<Integer, Byte>(len);
				// Iterate over keys
			 	for (Integer i = 0; i < len; i++) {
					// Check that key exists and is not null
			 		if (rulesJSON.has(i.toString()) && !rulesJSON.isNull(i.toString())) {
						// Check that value is a Number
			 			if (rulesJSON.get(i.toString()) instanceof Number) {
			 				newRules.put(i, rulesJSON.getNumber(i.toString()).byteValue());
			 			} else {
							// Associated value is not a Number
			 				return statusCode;
			 			}
			 		} else {
						// Key does not exist or value for associated key does not exist
			 			return statusCode;
			 		}
			 	}
			 	// If this part is reached the newRules are valid
			 	setRuleComplexity(ceil);
			 	rules = newRules;
				statusCode += 2;
			} else {
				// len is not a power of two
				return statusCode;
			}
		}
		return statusCode;
	}

	/**
	 * Analyze {@code statusCode} for a loaded {@code seed}.
	 * 
	 * @param statusCode return value from setFromJSON()
	 * @return {@code true} if {@code seed} was set from file
	 */
	public static boolean keepSeedFromStatusCode(int statusCode) {
		return (statusCode % 2) == 1;
	}

	/**
	 * Analyze {@code statusCode} for loaded {@code rules}.
	 * 
	 * @param statusCode return value from {@code setFromJSON()}
	 * @return {@code true} if {@code rules} were set from file
	 */
	public static boolean keepRulesFromStatusCode(int statusCode) {
		return statusCode > 1;
	}

	// GETTERS AND SETTERS

	/**
	 * Get the current {@code width} value, may be different compared to the width of {@code img}.
	 * 
	 * @return {@code width}
	 */
	public static int getWidth() {
		return Noiseed.width;
	}

	/**
	 * Set {@code width}.
	 * 
	 * @param newWidth the new value of {@code width}
	 */
	public static void setWidth(int newWidth) {
		if (newWidth > 0) {
			Noiseed.width = newWidth;
		}
	}

	/**
	 * Get {@code height}, may be different compared to the height of {@code img}.
	 * 
	 * @return {@code height}
	 */
	public static int getHeight() {
		return Noiseed.height;
	}

	/**
	 * Set {@code height}.
	 * 
	 * @param newHeight the new value of {@code height}
	 */
	public static void setHeight(int newHeight) {
		if (newHeight > 0) {
			Noiseed.height = newHeight;
		}
	}

	/**
	 * Get {@code n}, may be different compared to the n of {@code img}.
	 * 
	 * @return {@code n}
	 */
	public static int getRuleComplexity() {
		return Noiseed.n;
	}

	/**
	 * Set {@code n}.
	 * 
	 * @param newN the new value of {@code n}
	 */
	public static void setRuleComplexity(int newN) {
		if (newN >= 0) {
			Noiseed.n = newN;
		}
	}

	/**
	 * Get the current {@code img}.
	 * 
	 * @return {@code img}
	 */
	public static BufferedImage getImg() {
		return Noiseed.img;
	}

	/**
	 * Set {@code img}.
	 * 
	 * @param img the new BufferedImage
	 */
	public static void setImg(BufferedImage img) {
		Noiseed.img = img;
	}

	/**
	 * Get current progress.
	 * 
	 * @return value of progress
	 */
	public static int getGenerationProgress() {
		return Noiseed.generationProgress;
	}

	/**
	 * Set current {@code generationProgress}.
	 * 
	 * @param current sum of current estimated progress
	 * @param max sum of all estimated operation costs
	 */
	public static void setGenerationProgress(long current, long max) {
		// Divide first and multiply after to lessen the wrap-around risk
		// Get a value between 0.0 and 1.0
		double progress = (double) current / (double) max;
		// Get a value representing progress in % (0.0 to 100.0)
		progress *= 100;
		// clamp progress between 0 and 100
		Noiseed.generationProgress = Helper.clamp((int) Math.floor(progress), 0, 100);
	}

	/**
	 * Get the color that replaces 1s.
	 * 
	 * @return RGB color value that replaces 1s
	 */
	public static int getColorOne() {
		return Noiseed.colorOne;
	}

	/**
	 * Set a new RGB color value to replace 1s.
	 * 
	 * @param newColorOne the new RGB color value to replace 1s
	 */
	public static void setColorOne(int newColorOne) {
		Noiseed.colorOne = newColorOne;
	}

	/**
	 * Get the color that replaces 0s.
	 * 
	 * @return RGB color value that replaces 0s
	 */
	public static int getColorZero() {
		return Noiseed.colorZero;
	}

	/**
	 * Set a new RGB color value to replace 0s.
	 * 
	 * @param newColorZero the new RGB color value to replace 0s
	 */
	public static void setColorZero(int newColorZero) {
		Noiseed.colorZero = newColorZero;
	}

	/**
	 * Toggle progress tracking.
	 * 
	 * @param enable boolean flag determining activation of progress tracking
	 */
	public static void enableCalculateProgress(boolean enable) {
		Noiseed.calculateProgress = enable;
	}

	/**
	 * Get length (width) of the current {@code seed}, may be different from {@code width}.
	 * 
	 * @return the width of the current {@code seed}
	 */
	public static int getSeedLength() {
		return Noiseed.seed.length;
	}
	
	/**
	 * Get rule complexity of the current {@code rules}, may be different from {@code n}.
	 * 
	 * @return the value n where {@code rules.size() == 2^n}
	 */
	public static int getCurrentRulesN() {
		return (int)(Math.log(Noiseed.rules.size()) / Math.log(2));
	}
}
