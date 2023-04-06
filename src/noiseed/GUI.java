package noiseed;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import java.io.File;
import java.nio.file.Path;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.util.Arrays;

public class GUI implements ActionListener, ChangeListener {
	
	// Title
	public static final String PROGRAM_TITLE 			= "Noiseed";
	// Icon
	public static final String ICONFILENAME 			= "noiseed_icon.png";
	public static final String ICONFOLDER 				= "icon";

	public static final Path ICONPATH 					= Path.of(ICONFOLDER, ICONFILENAME);
	public static final ImageIcon ICON 					= new ImageIcon(ICONPATH.toString());

	public static final Dimension FRAME_MIN_SIZE    	= new Dimension(250, 250);
	public static final int SIZE_SPINNER_MIN_VALUE 		= 1;
	public static final int RULE_SPINNER_MIN_VALUE 		= 0;
	// DEFAULT for rulespinner
	public static final int RULE_SPINNER_DEFAULT_VALUE	= Noiseed.DEFAULT_N;
	public static final int SPINNER_STEP_VALUE 			= 1;
	// Overkill, but why not
	public static final int SPINNER_MAX_VALUE 			= Integer.MAX_VALUE;
	
	// DEFAULT preset
	public static final String DEFAULT_PRESET_SIZE		= 	"1920x1080";
	private static final String[] PRESETS				= {	"16x16", 
															"32x32", 
															"48x48", 
															"64x64", 
															"128x128", 
															"240x160", 
															"256x256", 
															"480x480", 
															"480x576", 
															"720x480", 
															"720x576", 
															"1280x720", 
															"1280x1080", 
															"1440x1080", 
															DEFAULT_PRESET_SIZE, 
															"3840x2160", 
															"7680x4320", 
															"15360x8640" };
	
	// Set default preset index
	public static final int DEFAULT_PRESET_IDX 	= Helper.getStringArrayIndex(DEFAULT_PRESET_SIZE, PRESETS);
	
	// Get int values from the preset strings
	private final int[] presetValues 			= PRESETS.length > 0 ? presetsToIntArray(PRESETS) : presetsToIntArray(new String[]{DEFAULT_PRESET_SIZE});
	private final Noiseed noiseed;
	
	private int screenWidth;
	private int screenHeight;
	
	private JFrame frame 						= new JFrame(PROGRAM_TITLE);
	private JLabel imgLabel;
	private JLabel presetLabel 					= new JLabel("Presets: ", null, SwingConstants.RIGHT);
	private JLabel widthLabel 					= new JLabel("Width: ", null, SwingConstants.RIGHT);
	private JLabel heightLabel 					= new JLabel("Height: ", null, SwingConstants.RIGHT);
	private JLabel ruleComplexityLabel 			= new JLabel("Rule complexity: ", null, SwingConstants.RIGHT);
	// Panel containing the control elements
	private JPanel controlPanel 				= new JPanel();
	// Panel displaying the image
	private JPanel imagePanel 					= new JPanel();
	// Make the image scrollable
	private JScrollPane scrollPane 				= new JScrollPane(imagePanel);
	private JProgressBar progressBar 			= new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
	private JButton generateButton   			= new JButton("Generate image");
	private JButton saveButton 					= new JButton("Save As...");
	private JButton loadButton 					= new JButton("Load JSON");
	private JButton colorButtonA 				= new JButton("Color A");
	private JButton colorButtonB 				= new JButton("Color B");
	private JCheckBox keepSeedCheckBox 			= new JCheckBox("Keep seed", false);
	private JCheckBox keepRulesCheckBox 		= new JCheckBox("Keep rules", false);	
	private JComboBox<String> presetComboBox 	= new JComboBox<>(PRESETS);
	private JSpinner widthSpinner 				= new JSpinner(new SpinnerNumberModel(	SIZE_SPINNER_MIN_VALUE, 	// Initial value
																						SIZE_SPINNER_MIN_VALUE,		// Minimum value
																						SPINNER_MAX_VALUE, 			// Maximum value
																						SPINNER_STEP_VALUE)); 		// Step
	private JSpinner heightSpinner 				= new JSpinner(new SpinnerNumberModel(	SIZE_SPINNER_MIN_VALUE, 	// Initial value
																						SIZE_SPINNER_MIN_VALUE,		// Minimum value	
																						SPINNER_MAX_VALUE, 			// Maximum value
																						SPINNER_STEP_VALUE)); 		// Step
	private JSpinner ruleComplexitySpinner 		= new JSpinner(new SpinnerNumberModel(	RULE_SPINNER_DEFAULT_VALUE, // Initial value
																						RULE_SPINNER_MIN_VALUE,		// Minimum value
																						SPINNER_MAX_VALUE, 			// Maximum value
																						SPINNER_STEP_VALUE)); 		// Step
	private String currentDir = new File("").getAbsolutePath();
	private JFileChooser saveAsFileChooser = new JFileChooser(currentDir);
	private JFileChooser loadFromFileChooser = new JFileChooser(currentDir);
	
	private ImageGenerator generator;
	
	private Action generateAction = new GenerateAction();
	private Action saveAction = new SaveAction();
	private Action loadAction = new LoadAction();
	private Action colorAAction = new ColorAAction();
	private Action colorBAction = new ColorBAction();
	private Action keepSeedAction  = new KeepSeedAction();
	private Action keepRulesAction = new KeepRulesAction();		
	private Action incrementWidthAction = new IncrementWidthAction();
	private Action decrementWidthAction = new DecrementWidthAction();		
	private Action incrementHeightAction = new IncrementHeightAction();
	private Action decrementHeightAction = new DecrementHeightAction();		
	private Action incrementRuleComplexityAction = new IncrementRuleComplexityAction();
	private Action decrementRuleComplexityAction = new DecrementRuleComplexityAction();
	
	// THREADED TASK CLASSES

	/**
	 * Generate images in separate thread.
	 */
	class ImageGenerator extends SwingWorker<Void, Void> {

		/*
         * Executed in background thread.
         */
        @Override
        public Void doInBackground() {
			// Start image generation
            noiseed.generateImage();
            return null;
        }

        /*
         * Executed in event dispatching thread.
         */
        @Override
        public void done() {
			// Display newly generated image
            updateImage();
			// Toggle off waiting
			toggleWaiting(false, generateButton);
        }
    }
	
	/**
	 * Update {@code progressBar} in separate thread.
	 */
	class ProgressBarUpdater extends SwingWorker<Void, Void> {
        /*
         * Executed in background thread.
         */
        @Override
        public Void doInBackground() {
			// Reset progressBar
			progressBar.setValue(0);
			// Periodically update progressBar
            while (!generator.isDone()) {
            	progressBar.setValue(noiseed.getGenerationProgress());
            	try {
					Thread.sleep(30);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }
            return null;
        }

        /*
         * Executed in event dispatching thread.
         */
        @Override
        public void done() {
        	// Set progressBar to 100%
        	progressBar.setValue(100);
        }
    }

	// ACTION CLASSES

	class GenerateAction extends AbstractAction {

		private static final long serialVersionUID = 2560744601307475324L;

		@Override
		public void actionPerformed(ActionEvent e) {
			generateButton.doClick();
		}
	}
	
	class SaveAction extends AbstractAction {

		private static final long serialVersionUID = 2913333663614586716L;

		@Override
		public void actionPerformed(ActionEvent e) {
			saveButton.doClick();
		}
	}
	
	class LoadAction extends AbstractAction {

		private static final long serialVersionUID = 2401147703598620496L;

		@Override
		public void actionPerformed(ActionEvent e) {
			loadButton.doClick();			
		}
	}
	
	class ColorAAction extends AbstractAction {

		private static final long serialVersionUID = -464849529571716841L;

		@Override
		public void actionPerformed(ActionEvent e) {
			colorButtonA.doClick();			
		}
	}
	
	class ColorBAction extends AbstractAction {

		private static final long serialVersionUID = 36070904879421503L;

		@Override
		public void actionPerformed(ActionEvent e) {
			colorButtonB.doClick();			
		}
	}
	
	class KeepSeedAction extends AbstractAction {

		private static final long serialVersionUID = -1285620423963433373L;

		@Override
		public void actionPerformed(ActionEvent e) {
			keepSeedCheckBox.doClick();			
		}
	}
	
	class KeepRulesAction extends AbstractAction {

		private static final long serialVersionUID = 8459557494685240522L;

		@Override
		public void actionPerformed(ActionEvent e) {
			keepRulesCheckBox.doClick();			
		}
	}
	
	class IncrementWidthAction extends AbstractAction {

		private static final long serialVersionUID = -7615104684573087063L;

		@Override
		public void actionPerformed(ActionEvent e) {
			// Setter clamps
			setWidthSpinner(noiseed.getWidth() + 1);
		}
	}
	
	class DecrementWidthAction extends AbstractAction {

		private static final long serialVersionUID = 4735788959117114279L;

		@Override
		public void actionPerformed(ActionEvent e) {
			// Setter clamps
			setWidthSpinner(noiseed.getWidth() - 1);			
		}
	}
	
	class IncrementHeightAction extends AbstractAction {

		private static final long serialVersionUID = -7170552381164150222L;

		@Override
		public void actionPerformed(ActionEvent e) {
			// Setter clamps
			setHeightSpinner(noiseed.getHeight() + 1);			
		}
	}
	
	class DecrementHeightAction extends AbstractAction {

		private static final long serialVersionUID = 8132822577542541392L;

		@Override
		public void actionPerformed(ActionEvent e) {
			// Setter clamps
			setHeightSpinner(noiseed.getHeight() - 1);			
		}		
	}
	
	class IncrementRuleComplexityAction extends AbstractAction {

		private static final long serialVersionUID = -4933428000742101812L;

		@Override
		public void actionPerformed(ActionEvent e) {
			// Setter clamps
			setRuleSpinner(noiseed.getRuleComplexity() + 1);			
		}		
	}
	
	class DecrementRuleComplexityAction extends AbstractAction {

		private static final long serialVersionUID = 8509788842486708299L;

		@Override
		public void actionPerformed(ActionEvent e) {
			// Setter clamps
			setRuleSpinner(noiseed.getRuleComplexity() - 1);			
		}		
	}

	// MAIN FUNCTION

	/**
	 * Start the Noiseed GUI.
	 * 
	 * @param args UNUSED
	 */
	public static void main(String[] args) {
		// Start GUI
        EventQueue.invokeLater(GUI::new);
	}

	// GUI CONSTRUCTOR

	public GUI() {
		// Initialize Noiseed object
		noiseed = new Noiseed();
		// Enable progress tracking for the progress bar
		noiseed.enableCalculateProgress(true);
				
		// Add ActionListeners
		generateButton.addActionListener(this);
		progressBar.setStringPainted(true);
		saveButton.addActionListener(this);
		loadButton.addActionListener(this);
		colorButtonA.addActionListener(this);
		colorButtonB.addActionListener(this);
		keepSeedCheckBox.addActionListener(this);
		keepRulesCheckBox.addActionListener(this);
		presetComboBox.addActionListener(this);
		
		// Add ChangeListeners
		widthSpinner.addChangeListener(this);
		heightSpinner.addChangeListener(this);
		ruleComplexitySpinner.addChangeListener(this);
		
		// Add key bindings to tooltips
		generateButton.setToolTipText("G");
		saveButton.setToolTipText("S");
		loadButton.setToolTipText("E");
		colorButtonA.setToolTipText("D");
		colorButtonB.setToolTipText("F");
		keepSeedCheckBox.setToolTipText("W");
		keepRulesCheckBox.setToolTipText("R");
		widthSpinner.setToolTipText("(-) LEFT/RIGHT (+)");
		heightSpinner.setToolTipText("(-) DOWN/UP (+)");
		ruleComplexitySpinner.setToolTipText("(-) A/Q (+)");
		
		// SET UP KEY BINDINGS
		// ORDER MATTERS
		String[] actionMapKeys = {	"Generate",
									"Save",
									"Load",
									"ColorA",
									"ColorB",
									"KeepSeed",
									"KeepRules",
									"IncrementWidth",
									"DecrementWidth",
									"IncrementHeight",
									"DecrementHeight",
									"IncrementRuleComplexity",
									"DecrementRuleComplexity"};
		// ORDER MATTERS
		int[] keyCodes = {	KeyEvent.VK_G,
							KeyEvent.VK_S,
							KeyEvent.VK_E,
							KeyEvent.VK_D,
							KeyEvent.VK_F,
							KeyEvent.VK_W,
							KeyEvent.VK_R,
							KeyEvent.VK_RIGHT,
							KeyEvent.VK_LEFT,
							KeyEvent.VK_UP,
							KeyEvent.VK_DOWN,
							KeyEvent.VK_Q,
							KeyEvent.VK_A};
		// ORDER MATTERS
		Action[] actions = {generateAction, 
							saveAction, 
							loadAction, 
							colorAAction, 
							colorBAction, 
							keepSeedAction, 
							keepRulesAction, 
							incrementWidthAction, 
							decrementWidthAction, 
							incrementHeightAction,
							decrementHeightAction, 
							incrementRuleComplexityAction,
							decrementRuleComplexityAction};
		
		// FIRST N ENTRIES ARE ON_RELEASE EVENTS
		int indexSetOnrelease = 6;
		// SETUP KEY BINDINGS BY LOOPING THROUGH THE ARRAYS
		if ((actionMapKeys.length == keyCodes.length) && (actionMapKeys.length == actions.length)) {
			for (int i = 0; i < actionMapKeys.length; i++) {
				controlPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
						// getKeyStroke​(int keyCode, int modifiers, boolean onKeyRelease)
						KeyStroke.getKeyStroke(keyCodes[i], 0, i <= indexSetOnrelease), actionMapKeys[i]);
				controlPanel.getActionMap().put(actionMapKeys[i], actions[i]);
			}
		}
		
		// Set background color
		progressBar.setBackground(Color.WHITE);
		keepSeedCheckBox.setBackground(Color.BLACK);
		keepRulesCheckBox.setBackground(Color.BLACK);
		controlPanel.setBackground(Color.BLACK);
		imagePanel.setBackground(Color.BLACK);
		
		// Set foreground color
		progressBar.setForeground(Color.BLACK);
		keepSeedCheckBox.setForeground(Color.WHITE);
		keepRulesCheckBox.setForeground(Color.WHITE);
		widthLabel.setForeground(Color.WHITE);
		heightLabel.setForeground(Color.WHITE);
		ruleComplexityLabel.setForeground(Color.WHITE);
		presetLabel.setForeground(Color.WHITE);
		
		// Set up saveAsFileChooser
		String saveDescription = Helper.getFileFormatFilterDescription("Images", Noiseed.getAvailableFormats());
		FileFilter saveFilter = new FileNameExtensionFilter(saveDescription, Noiseed.getAvailableFormats());
		saveAsFileChooser.removeChoosableFileFilter(saveAsFileChooser.getAcceptAllFileFilter());
		saveAsFileChooser.setFileFilter(saveFilter);
		
		// Set up loadFromFileChooser
		FileFilter loadFilter = new FileNameExtensionFilter("JSON (*.json)",  new String[]{"json"});
		loadFromFileChooser.removeChoosableFileFilter(loadFromFileChooser.getAcceptAllFileFilter());
		loadFromFileChooser.setFileFilter(loadFilter);
				
		// Needs to happen after widthSpinner and heightSpinner have their listeners added
		// This also sets the width and height in Noiseed
		presetComboBox.setSelectedIndex(DEFAULT_PRESET_IDX);
		// Generate image on start-up after width, height, n are set
		noiseed.generateImage();
		
		// Set up progress bar
		progressBar.setValue(100);
		progressBar.setBorderPainted(false);
		progressBar.setPreferredSize(new Dimension(1, 20));
		// Add image label
		imgLabel = new JLabel();
		// Set image to label
		updateImage();
		imagePanel.add(imgLabel);
		
		// Set up controlPanel
		controlPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.WHITE));
		controlPanel.setLayout(new GridLayout(2, 0));

		// Add the first row of elements
		controlPanel.add(generateButton);
		controlPanel.add(saveButton);
		controlPanel.add(loadButton);
		controlPanel.add(colorButtonA);
		controlPanel.add(colorButtonB);
		// Empty JLabel for 1 unit of space
		controlPanel.add(new JLabel());
		controlPanel.add(keepSeedCheckBox);
		controlPanel.add(keepRulesCheckBox);

		// Add the second row of elements
		controlPanel.add(presetLabel);
		controlPanel.add(presetComboBox);
		controlPanel.add(widthLabel);
		controlPanel.add(widthSpinner);
		controlPanel.add(heightLabel);
		controlPanel.add(heightSpinner);
		controlPanel.add(ruleComplexityLabel);
		controlPanel.add(ruleComplexitySpinner);

		// Set up scrollPane
		scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		scrollPane.setFocusable(true);

		// Control panel at the top
		frame.add(controlPanel, BorderLayout.NORTH);
		// Image display in the center
		frame.add(scrollPane, BorderLayout.CENTER);
		// Progress bar at the bottom
		frame.add(progressBar, BorderLayout.SOUTH);

		// Set up the frame
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setIconImage(ICON.getImage());
		frame.setMinimumSize(FRAME_MIN_SIZE);
		frame.getContentPane().setBackground(Color.BLACK);

		// Set up frame size
		detectScreensize();
		frame.pack();
		if ((frame.getSize().getWidth() >= screenWidth) || (frame.getSize().getHeight() >= screenHeight)) {
			frame.setExtendedState(Frame.MAXIMIZED_BOTH); 
		}
		// Display GUI
		frame.setVisible(true);
	}
	
	// MAIN FUNCTIONALITY
	
	/**
	 * Set the image in {@code imgLabel} according to {@code noiseed.img}.
	 */
	private void updateImage() {
		imgLabel.setIcon(new ImageIcon(noiseed.getImg()));
	}

	/**
	 * Toggle certain buttons on/off.
	 * 
	 * @param enable if {@code true}, toggle buttons on
	 * @param currentButton button which regains focus after re-enabling buttons
	 */
	private void enableButtons(boolean enable, JButton currentButton) {
    	generateButton.setEnabled(enable);
    	saveButton.setEnabled(enable);
    	loadButton.setEnabled(enable);
    	colorButtonA.setEnabled(enable);
		colorButtonB.setEnabled(enable);
		if (enable) {
			currentButton.requestFocusInWindow();
		}
	}
	
	/**
	 * Toggle certain buttons and the waiting cursor on/off.
	 * 
	 * @param waiting if {@code true}, toggle buttons off and display waiting cursor
	 * @param focusedButton button which regains focus after re-enabling buttons
	 */
	private void toggleWaiting(boolean waiting, JButton focusedButton) {
		// Toggle buttons on/off
		enableButtons(!waiting, focusedButton);
		// Turn the waiting cursor on/off
		frame.setCursor(waiting ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
	}

	/**
	 * Create an int array from presets. Two entries per preset.
	 * <p>
	 * For example: [1920,1080] = presetsToIntArray(["1920x1080"]).
	 * 
	 * @param presets array containing Strings of the form "widthxheight"
	 * @return an int array of the preset values
	 */
	private int[] presetsToIntArray(String[] presets) {
		int numberOfElements = presets.length;
		// 2 values per preset entry; width and height
		int[] intValues = new int[numberOfElements * 2];
		for (int i = 0; i < numberOfElements; i++) {
			String[] strValues = presets[i].split("x");
			// Access pattern for the new int[]
			// presetIndex * 2 = width
			intValues[i * 2] = Integer.parseInt(strValues[0]);
			// (presetIndex * 2) + 1 = height
			intValues[(i * 2) + 1] = Integer.parseInt(strValues[1]);
		}
		return intValues;
	}

	/**
	 * Set {@code screenWidth} and {@code screenHeight} according to detected values.
	 */
	private void detectScreensize() {
		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		screenWidth = gd.getDisplayMode().getWidth();
		screenHeight = gd.getDisplayMode().getHeight();
	}

	// EVENT HANDLERS

	/**
	 * Handle button clicks, including keyboard shortcuts that call {@code doClick()}.
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		// Generate button
		if (e.getSource() == generateButton) {
			generateButtonHandler();
		// Save button
		} else if (e.getSource() == saveButton) {
			saveAsButtonHandler();
		// Load button
		} else if (e.getSource() == loadButton) {
			loadButtonHandler();
		// Preset combo box
		} else if (e.getSource() == presetComboBox) {
			setWidthSpinner(presetValues[presetComboBox.getSelectedIndex() * 2]);
			setHeightSpinner(presetValues[(presetComboBox.getSelectedIndex() * 2) + 1]);
		// Color A button
		} else if (e.getSource() == colorButtonA) {
			colorButtonHandler(true);
		// Color B button
		} else if (e.getSource() == colorButtonB) {
			colorButtonHandler(false);
		// Keep seed checkbox
		} else if (e.getSource() == keepSeedCheckBox) {
			noiseed.setKeepCurrentSeed(keepSeedCheckBox.isSelected());
			setWidthSpinner(noiseed.getSeedLength());
		// Keep rules checkbox
		} else if (e.getSource() == keepRulesCheckBox) {
			noiseed.setKeepCurrentRules(keepRulesCheckBox.isSelected());
			setRuleSpinner(noiseed.getCurrentRulesN());
		}
	}

	/**
	 * Handle spinner state changes.
	 */
	@Override
	public void stateChanged(ChangeEvent e) {
		// Width spinner
		if (e.getSource() == widthSpinner) {
			noiseed.setWidth((Integer)widthSpinner.getValue());
			// new width => new seed
			if (keepSeedCheckBox.isSelected() && (noiseed.getWidth() != noiseed.getSeedLength())) {
				noiseed.setKeepCurrentSeed(false);
				keepSeedCheckBox.setSelected(false);
			}
		// Height spinner
		} else if (e.getSource() == heightSpinner) {
			noiseed.setHeight((Integer)heightSpinner.getValue());
		// Rule complexity spinner
		} else if (e.getSource() == ruleComplexitySpinner) {
			noiseed.setRuleComplexity((Integer)ruleComplexitySpinner.getValue());
			// new n => new rules
			if (keepRulesCheckBox.isSelected() && (noiseed.getRuleComplexity() != noiseed.getCurrentRulesN())) {
				noiseed.setKeepCurrentRules(false);
				keepRulesCheckBox.setSelected(false);
			}
		}
	}

	// BUTTON HANDLERS

	/**
	 * Start image generation and progress bar thread.
	 */
	private void generateButtonHandler() {
		// Draw focus so that it does not "jump" to keep seed after toggleWaiting()
		scrollPane.requestFocusInWindow();
		// Toggle on waiting
		toggleWaiting(true, generateButton);
		// Instances of javax.swing.SwingWorker are not reusable
		// Create new instances as needed
		// Run the image creation on a separate thread
		generator = new ImageGenerator();
		generator.execute();
		// Run progress bar on a separate thread
		ProgressBarUpdater updater = new ProgressBarUpdater();
		updater.execute();
	}

	/**
	 * Show save dialog and save to file.
	 */
	private void saveAsButtonHandler() {
		// Toggle on waiting
		toggleWaiting(true, saveButton);
		// Set a default format
		String defaultFormat;
		if (Arrays.stream(Noiseed.getAvailableFormats()).anyMatch(Noiseed.DEFAULT_IMAGE_FORMAT::equalsIgnoreCase)) {
			defaultFormat = Noiseed.DEFAULT_IMAGE_FORMAT;
		} else {
			defaultFormat = Noiseed.getAvailableFormats()[0].toLowerCase();
		}
		// Set a default file name
		saveAsFileChooser.setSelectedFile(new File(Helper.dateTimeToString() + "." + defaultFormat));
		// Show save dialog
		int returnState = saveAsFileChooser.showSaveDialog(frame);
		if (returnState == JFileChooser.APPROVE_OPTION) {
			String filepath = saveAsFileChooser.getSelectedFile().getAbsolutePath();
			int splitOn = filepath.lastIndexOf(".");
			if (splitOn != -1) {
				String fileName = filepath.substring(0, splitOn);
				String format = filepath.substring(splitOn + 1);
				// Check if a writer for the specified format exists
				if (Arrays.stream(Noiseed.getAvailableFormats()).anyMatch(format::equalsIgnoreCase)) {
					// Save image to file
					boolean success = noiseed.saveImage(fileName, format);
					if (success) {
						// Save image info to file
						Noiseed.saveJSON(fileName + "_info", noiseed.createInfoJSONObject());
					} else {
						JOptionPane.showMessageDialog(frame, "Image could not be saved.\nAppropriate writer for format ." + format + " not found.", "Writer not found", JOptionPane.ERROR_MESSAGE);
					}
				} else {
					JOptionPane.showMessageDialog(frame, "Specified format ." + format + " is not supported.", "Unsupported format", JOptionPane.WARNING_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(frame, "Please specify a format.", "Format missing", JOptionPane.WARNING_MESSAGE);
			}
		}
		// Toggle off waiting
		toggleWaiting(false, saveButton);
	}

	/**
	 * Show load dialog to set {@code seed} and/or {@code rules}.
	 */
	private void loadButtonHandler() {
		// Set a default file name
		loadFromFileChooser.setSelectedFile(new File(Helper.dateTimeToString() + ".json"));
		// Show load dialog
		int returnState = loadFromFileChooser.showOpenDialog(frame);
		if (returnState == JFileChooser.APPROVE_OPTION) {
			String filepath = loadFromFileChooser.getSelectedFile().getAbsolutePath();
			int splitOn = filepath.lastIndexOf(".json");
			if (splitOn != -1) {
				// Set seed and rules from file
				int statusCode = noiseed.setFromJSON(filepath, true, true);
				if (statusCode == 0) {
					JOptionPane.showMessageDialog(frame, "Values could not be loaded from " + filepath + ".", "Load error", JOptionPane.ERROR_MESSAGE);
				} else {
					// width and n may have changed
					setWidthSpinner(noiseed.getWidth());
					setRuleSpinner(noiseed.getRuleComplexity());
					// Set checkboxes only if the corresponding value was loaded
					noiseed.setKeepCurrentSeed(Noiseed.keepSeedFromStatusCode(statusCode));
					keepSeedCheckBox.setSelected(Noiseed.keepSeedFromStatusCode(statusCode));
					noiseed.setKeepCurrentRules(Noiseed.keepRulesFromStatusCode(statusCode));
					keepRulesCheckBox.setSelected(Noiseed.keepRulesFromStatusCode(statusCode));
					// Generate image from given info
					generateButton.doClick();
				}
			} else {
				JOptionPane.showMessageDialog(frame, "Please select a .json file.", "Wrong format", JOptionPane.WARNING_MESSAGE);
			}
		}
	}

	/**
	 * Show color picker and set the selected color.
	 * 
	 * @param isColorA {@code true} if called in context of {@code colorButtonA} 
	 */
	private void colorButtonHandler(boolean isColorA) {
		JButton currentColorButton = isColorA ? colorButtonA : colorButtonB;
		// Toggle on waiting
		toggleWaiting(true, currentColorButton);
		String activeColorString = isColorA ? "Color A" : "Color B";
		int currentColor = isColorA ? noiseed.getColorOne() : noiseed.getColorZero();
		// Show color picker dialog
		Color newColor = JColorChooser.showDialog(frame, activeColorString, new Color(currentColor));
		if (newColor != null && newColor.getRGB() != currentColor) {
			// Set the new RGB value
			if (isColorA) {
				noiseed.setColorOne(newColor.getRGB());
			} else {
				noiseed.setColorZero(newColor.getRGB());
			}
			// Change img color in noiseed
			noiseed.changeImageRGB(newColor.getRGB(), isColorA);
			// Reflect changes in GUI
			updateImage();
		}
		// Toggle off waiting
		toggleWaiting(false, currentColorButton);
	}

	// SETTERS

	/**
	 * Sets value for {@code widthSpinner} if it is within bounds.
	 * 
	 * @param newSpinnerWidth new value displayed in {@code widthSpinner}
	 */
	private void setWidthSpinner(int newSpinnerWidth) {
		// Clamp new values before calling widthSpinner.setValue()
		// Spinners have no inherent bound checking
		widthSpinner.setValue(Helper.clamp(newSpinnerWidth, SIZE_SPINNER_MIN_VALUE, SPINNER_MAX_VALUE));
	}

	/**
	 * Sets value for {@code heightSpinner} if it is within bounds.
	 * 
	 * @param newSpinnerHeight new value displayed in {@code heightSpinner}
	 */
	private void setHeightSpinner(int newSpinnerHeight) {
		// Clamp new values before calling heightSpinner.setValue()
		// Spinners have no inherent bound checking
		heightSpinner.setValue(Helper.clamp(newSpinnerHeight, SIZE_SPINNER_MIN_VALUE, SPINNER_MAX_VALUE));
	}

	/**
	 * Sets value for {@code ruleComplexitySpinner} if it is within bounds.
	 * 
	 * @param newSpinnerN new value displayed in {@code ruleComplexitySpinner}
	 */
	private void setRuleSpinner(int newSpinnerN) {
		// Clamp new values before calling ruleComplexitySpinner.setValue()
		// Spinners have no inherent bound checking
		ruleComplexitySpinner.setValue(Helper.clamp(newSpinnerN, RULE_SPINNER_MIN_VALUE, SPINNER_MAX_VALUE));
	}
}
