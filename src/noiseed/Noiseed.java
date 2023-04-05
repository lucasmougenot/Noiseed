package noiseed;

import java.awt.Color;
import java.awt.Graphics2D;
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
import java.util.concurrent.ThreadLocalRandom;

import org.json.JSONArray;
import org.json.JSONObject;

public class Noiseed {

	// Default image sizes
	public static final int DEFAULT_WIDTH = 1920;
	public static final int DEFAULT_HEIGHT = 1080;
	// Default rule complexity
	public static final int DEFAULT_N = 5;
	// Default colors
	public static final int DEFAULT_COLOR_ZERO = Color.BLACK.getRGB();
	public static final int DEFAULT_COLOR_ONE = Color.WHITE.getRGB();
	// Default flags
	public static final boolean DEFAULT_KEEP_CURRENT_SEED = false;
	public static final boolean DEFAULT_KEEP_CURRENT_RULES = false;

	// JSON keys
	public static final String SEEDKEY = "seed";
	public static final String RULEKEY = "rules";
	// Default image format
	public static final String DEFAULT_IMAGE_FORMAT = "png";
	// Percentage ratio estimates
	public static final int ROWLIST_COST_WEIGHT	= 40;
	public static final int IMAGE_COST_WEIGHT	= 60;

	// Width and height of the image that is to be generated
	// Width and height both need to be > 0
	private int width;
	private int height;
	// Rule complexity (2^n rule entries)
	// n >= 0
	// For example n = 0 = 2^0 = 1 = single rule which is applied to all entries
	private int n;

	// The 2 colors to replace the values 0 (colorZero) and 1 (colorOne) in rowList
	private int colorZero;
	private int colorOne;

	// Flags to retain current seed and/or rules
	private boolean keepCurrentSeed;
	private boolean keepCurrentRules;

	// First row of the image containing 0s and 1s
	private byte[] seed;
	// (1 << n) is equivalent to 2**n | 2^n | two to the power of n
	private HashMap<Integer, Byte> rules = new HashMap<Integer, Byte>(1 << n);

	// 2-dimensional array representing the image containing 0s and 1s
	private byte[][] rowList;

	// Holds the generated image
	private BufferedImage img;

	// Get an array of available formats
	public static String[] availableFormats = ImageIO.getWriterFormatNames();

	// Represents percentage (0 - 100)
	private int generationProgress = 0;
	// Used to estimate and calculate generationProgress
	private long currentTotal = 0;
	private long maxTotal	  = 0;
	// Flag that can be set externally via enableCalculateProgress()
	private boolean calculateProgress = false;
	// Initialize estimates for progress calculation
	private long rowListCost = 0;
	private long imageCost   = 0;

	public Noiseed(int width, int height, int n, int colorZero, int colorOne, boolean keepCurrentSeed, boolean keepCurrentRules) {
		setWidth(width);
		setHeight(height);
		setRuleComplexity(n);
		setColorZero(colorZero);
		setColorOne(colorOne);
		setKeepCurrentSeed(keepCurrentSeed);
		setKeepCurrentRules(keepCurrentRules);
	}

	public Noiseed(int width, int height, int n, int colorZero, int colorOne) {
		this(width, height, n, colorZero, colorOne, DEFAULT_KEEP_CURRENT_SEED, DEFAULT_KEEP_CURRENT_RULES);
	}

	public Noiseed(int width, int height, int n) {
		this(width, height, n, DEFAULT_COLOR_ZERO, DEFAULT_COLOR_ONE, DEFAULT_KEEP_CURRENT_SEED, DEFAULT_KEEP_CURRENT_RULES);
	}

	public Noiseed(int width, int height) {
		this(width, height, DEFAULT_N, DEFAULT_COLOR_ZERO, DEFAULT_COLOR_ONE, DEFAULT_KEEP_CURRENT_SEED, DEFAULT_KEEP_CURRENT_RULES);
	}

	public Noiseed() {
		this(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_N, DEFAULT_COLOR_ZERO, DEFAULT_COLOR_ONE, DEFAULT_KEEP_CURRENT_SEED, DEFAULT_KEEP_CURRENT_RULES);
	}

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

		// Initialize default Noiseed object
		Noiseed noiseed = new Noiseed();

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
				noiseed.setWidth(Integer.parseInt(args[INDEX_WIDTH]));
			}
			// Height of image
			if (args.length > INDEX_HEIGHT) {
				noiseed.setHeight(Integer.parseInt(args[INDEX_HEIGHT]));
			}
			// Rule complexity of rules (2^n rules)
			if (args.length > INDEX_N) {
				noiseed.setRuleComplexity(Integer.parseInt(args[INDEX_N]));
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
				statusCodeInfoFile = noiseed.setFromJSON(infoFileName, true, true);
			}
		}

		// Generate images infinitely or according to amount
		while (amountToBeGenerated > 0) {
			// true for statusCode 1 or 3
			noiseed.setKeepCurrentSeed(keepSeedFromStatusCode(statusCodeInfoFile));
			// true for statusCode 2 or 3
			noiseed.setKeepCurrentRules(keepRulesFromStatusCode(statusCodeInfoFile));
			// Generate the image
			noiseed.generateImage();

			// Generate a fileName
			String fileName = Helper.dateTimeToString();
			String fullImageFileName = Helper.setFileName(fileName, format);

			// Check if a file with fileName already exists
			Path path = Path.of(fullImageFileName);
			boolean fileExists = Files.exists(path);

			// Limit retries for following file existence checking
			int retries = 5;
			// If file already exists, regenerate a fileName
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
			boolean imageSaved = noiseed.saveImage(fileName, format);
			if (!imageSaved) {
				System.out.println("Could not save image file " + fullImageFileName);
			}
			// Save info file
			String infoFileName = fileName + "_info";
			boolean infoSaved = saveJSON(infoFileName, noiseed.createInfoJSONObject());
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
	 */
	public void generateImage() {
		// Check if progress tracking is desired
		if (calculateProgress) {
			// Reset progress to 0 (%)
			setGenerationProgress(0, 100);
			currentTotal = 0;
			// Very rough estimates for progress display, only count progress per row
			// Circa ROWLIST_COST_WEIGHT % of compute time
			rowListCost = ((long) height - 1) * ROWLIST_COST_WEIGHT;
			// Circa IMAGE_COST_WEIGHT % of compute time
			imageCost = (long) height * IMAGE_COST_WEIGHT;
			maxTotal = rowListCost + imageCost;
		}

		// Generate seed if needed
		if (!keepCurrentSeed) {
			seed = createSeed();
		}

		// Generate rules if needed
		if (!keepCurrentRules) {
			rules = createRules();
		}

		// Generate 2-D array representing pixels
		rowList = createRowList();

		// Initialize new BufferedImage
		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		// Set each pixel according to the associated rowList entry
		setImageRGB();
	}

	/**
	 * Generate a seed for image generation containing 0s and 1s.
	 * 
	 * @return byte array of size width containing 0s and 1s
	 */
	public byte[] createSeed() {
		byte[] newSeed = new byte[width];
		for (int i = 0; i < width; i++) {
			newSeed[i] = (byte) ThreadLocalRandom.current().nextInt(0, 2);
		}
		return newSeed;
	}

	/**
	 * Generate a ruleset for image generation.
	 * 
	 * @return {@code Hashmap} with 2^n rules, each rule represented by a Key (1 to 2^n) and Value (1 or 0)
	 */
	public HashMap<Integer, Byte> createRules() {
		HashMap<Integer, Byte> newRules = new HashMap<Integer, Byte>(1 << n);
		for (int i = 0; i < (1 << n); i++) {
			newRules.put(i, (byte) ThreadLocalRandom.current().nextInt(0, 2));
		}
		return newRules;
	}

	/**
	 * Create a 2-D array for an image based on {@code seed} and {@code rules}, containing 0s and 1s.
	 * 
	 * @return 2-D array representing the image containing 0s and 1s as entries
	 */
	public byte[][] createRowList() {
		byte[][] newRowList = new byte[height][width];
		newRowList[0] = seed;
		// y ==> row-index (y-coordinate)
		for (int y = 1; y < height; y++) {
			byte[] nextRow = new byte[width];
			// Set ruleKey to 0 for each row
			int ruleKey = 0;
			// x ==> column-index (x-coordinate) of current row
			for (int x = 0; x < width; x++) {
				// IMPORTANT for n == 0, there is just 1 rule
				// Therefore ruleKey does not need to be recalculated and always remains 0
				if (n == 0) {}
				// Calculate initial window once per loop
				else if (x == 0) {
					// k ==> position of entry in [window] to calculate entry x
					for (int k = 0; k < n; k++) {
						// Example for n = 2
						// (row y-1) ... | ... | ... [ MSB | LSB ] ... | ...
						// (row y)   ... | x-2 | x-1 |  x  | x+1 | x+2 | ...
						// Example for n = 3
						// (row y-1) ... | ... [ MSB | BIT | LSB ] ... | ...
						// (row y)   ... | x-2 | x-1 |  x  | x+1 | x+2 | ...
						if (newRowList[y - 1][Math.floorMod((n / 2) - k, width)] == 1) {
							ruleKey += 1 << k;
						}
					}
				// Shift window to the right for next entries
				// Cut off MSB, left shift and set LSB correctly
				} else {
					// Set MSB to 0 by bitwise AND with 01111...
					ruleKey &= ~(1 << (n - 1));
					// Shift left by 1
					ruleKey <<= 1;
					// Check rightmost entry of window
					if (newRowList[y - 1][Math.floorMod((n / 2) + x, width)] == 1) {
						// Set LSB to 1 by bitwise OR with ...00001
						ruleKey |= 1;
					}
				}
				// Set the entry x according to calculated ruleKey
				nextRow[x] = rules.get(ruleKey);
			}
			// Add the newly generated row to newRowList
			newRowList[y] = nextRow;
			// Keep track of "progress"
			if (calculateProgress) {
				currentTotal += ROWLIST_COST_WEIGHT;
				setGenerationProgress(currentTotal, maxTotal);
			}
		}
		return newRowList;
	}

	/**
	 * Set colors in {@code img} based on {@code rowList}.
	 */
	private void setImageRGB() {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				img.setRGB(x, y, rowList[y][x] == 1 ? colorOne : colorZero);
			}
			// Keep track of "progress"
			if (calculateProgress) {
				currentTotal += IMAGE_COST_WEIGHT;
				setGenerationProgress(currentTotal, maxTotal);
			}
		}
	}

	/**
	 * Change one color in {@code img} based on {@code rowList}.
	 * 
	 * @param newColor the new RGB value
	 * @param changeColorOne determines the pixels which will have their RGB value changed
	 */
	public void changeImageRGB(int newColor, boolean changeColorOne) {
		// Control which color is changed
		int colorToChange = changeColorOne ? 1 : 0;
		// Loop through each rowList entry
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				// Only change 1s XOR 0s
				if (rowList[y][x] == colorToChange) {
					// Replace single RGB values in img
					img.setRGB(x, y, newColor);
				}
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
	public boolean saveImage(String fileName, String format) {
		File f = new File(Helper.setFileName(fileName, format));
		BufferedImage saveImage;
		// wbmp is supposed to be always supported
		if (format.equalsIgnoreCase("wbmp")) {
			// Need imageType to be TYPE_BYTE_BINARY
			BufferedImage oneBitImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
			// Draw into oneBitImage via Graphics2D object
			Graphics2D oneBitG2D = oneBitImage.createGraphics();
			// Redraw img in oneBitImage
			oneBitG2D.drawImage(img, 0, 0, null);
			// ...preferable to manually free the associated resources by calling this method...
			oneBitG2D.dispose();
			// Assign the newly constructed image
			saveImage = oneBitImage;
		} else {
			// Assign the current image
			saveImage = img;
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
	public JSONObject createInfoJSONObject() {
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
	public int setFromJSON(String fileName, boolean setSeed, boolean setRules) {
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
			int ceil = (int) (Math.ceil(Math.log(len) / Math.log(2)));
			int floor = (int) (Math.floor(Math.log(len) / Math.log(2)));
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
	public int getWidth() {
		return width;
	}

	/**
	 * Set {@code width}.
	 * 
	 * @param newWidth the new value of {@code width}
	 */
	public void setWidth(int newWidth) {
		if (newWidth > 0) {
			width = newWidth;
		}
	}

	/**
	 * Get {@code height}, may be different compared to the height of {@code img}.
	 * 
	 * @return {@code height}
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Set {@code height}.
	 * 
	 * @param newHeight the new value of {@code height}
	 */
	public void setHeight(int newHeight) {
		if (newHeight > 0) {
			height = newHeight;
		}
	}

	/**
	 * Get {@code n}, may be different compared to the n of {@code img}.
	 * 
	 * @return {@code n}
	 */
	public int getRuleComplexity() {
		return n;
	}

	/**
	 * Set {@code n}.
	 * 
	 * @param newN the new value of {@code n}
	 */
	public void setRuleComplexity(int newN) {
		if (newN >= 0) {
			n = newN;
		}
	}

	/**
	 * Get a copy of the current {@code seed}.
	 * 
	 * @return {@code seed}
	 */
	public byte[] getSeed() {
		// Copy so that seed can not be modified by returned reference
		byte[] copy = new byte[getSeedLength()];
    	System.arraycopy(seed, 0, copy, 0, getSeedLength());
		return copy;
	}

	/**
	 * Set {@code seed}.
	 * 
	 * @param newSeed the new {@code seed} 
	 */
	public void setSeed(byte[] newSeed) {
		// Copy so that seed can not be modified by newSeed reference
		seed = new byte[newSeed.length];
    	System.arraycopy(newSeed, 0, seed, 0, newSeed.length);
	}

	/**
	 * Get a copy of the current {@code rules}.
	 * 
	 * @return {@code rules}
	 */
	public HashMap<Integer, Byte> getRules() {
		// Copy so that rules can not be modified by returned reference
		HashMap<Integer, Byte> copy = new HashMap<Integer, Byte>(rules);
		return copy;
	}

	/**
	 * Set {@code rules}.
	 * 
	 * @param newRules the new {@code rules}
	 */
	public void setRules(HashMap<Integer, Byte> newRules) {
		// Copy so that rules can not be modified by newRules reference
		rules = new HashMap<Integer, Byte>(newRules);
	}

	/**
	 * Get a copy of the current {@code img}.
	 * 
	 * @return {@code img}
	 */
	public BufferedImage getImg() {
		BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
		copy.setData(img.getData());
		return copy;
	}

	/**
	 * Get current progress.
	 * 
	 * @return value of progress
	 */
	public int getGenerationProgress() {
		return generationProgress;
	}

	/**
	 * Set current {@code generationProgress}.
	 * 
	 * @param current sum of current estimated progress
	 * @param max sum of all estimated operation costs
	 */
	public void setGenerationProgress(long current, long max) {
		// Divide first and multiply after to lessen the wrap-around risk
		// Get a value between 0.0 and 1.0
		double progress = (double) current / (double) max;
		// Get a value representing progress in % (0.0 to 100.0)
		progress *= 100;
		// clamp progress between 0 and 100
		generationProgress = Helper.clamp((int) Math.floor(progress), 0, 100);
	}

	/**
	 * Get the color that replaces 1s.
	 * 
	 * @return RGB color value that replaces 1s
	 */
	public int getColorOne() {
		return colorOne;
	}

	/**
	 * Set a new RGB color value to replace 1s.
	 * 
	 * @param newColorOne the new RGB color value to replace 1s
	 */
	public void setColorOne(int newColorOne) {
		colorOne = newColorOne;
	}

	/**
	 * Get the color that replaces 0s.
	 * 
	 * @return RGB color value that replaces 0s
	 */
	public int getColorZero() {
		return colorZero;
	}

	/**
	 * Set a new RGB color value to replace 0s.
	 * 
	 * @param newColorZero the new RGB color value to replace 0s
	 */
	public void setColorZero(int newColorZero) {
		colorZero = newColorZero;
	}

	/**
	 * Get {@code keepCurrentSeed}.
	 * 
	 * @return {@code keepCurrentSeed}
	 */
	public boolean getKeepCurrentSeed() {
		return keepCurrentSeed;
	}
	
	/**
	 * Set {@code keepCurrentSeed}.
	 * 
	 * @param newKeepCurrentSeed the new value of determing keeping the current seed
	 */
	public void setKeepCurrentSeed(boolean newKeepCurrentSeed) {
		keepCurrentSeed = newKeepCurrentSeed;
	}

	/**
	 * Get {@code keepCurrentRules}.
	 * 
	 * @return {@code keepCurrentRules}
	 */
	public boolean getKeepCurrentRules() {
		return keepCurrentRules;
	}

	/**
	 * Set {@code keepCurrentSeed}.
	 * 
	 * @param newKeepCurrentRules the new value of determing keeping the current rules
	 */
	public void setKeepCurrentRules(boolean newKeepCurrentRules) {
		keepCurrentRules = newKeepCurrentRules;
	}

	/**
	 * Toggle progress tracking.
	 * 
	 * @param enable boolean flag determining activation of progress tracking
	 */
	public void enableCalculateProgress(boolean enable) {
		calculateProgress = enable;
	}

	/**
	 * Get length (width) of the current {@code seed}, may be different from {@code width}.
	 * 
	 * @return the width of the current {@code seed}
	 */
	public int getSeedLength() {
		return seed.length;
	}

	/**
	 * Get rule complexity of the current {@code rules}, may be different from {@code n}.
	 * 
	 * @return the value n where {@code rules.size() == 2^n}
	 */
	public int getCurrentRulesN() {
		return (int) (Math.log(rules.size()) / Math.log(2));
	}
}
