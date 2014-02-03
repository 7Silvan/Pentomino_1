
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * This class solves pentominos puzzles.  A pentomino consists of 5 connected squares.  There are exactly
 * 12 ways to make a pentomino (counting rotations and reflections of a piece as the same piece).  A
 * pentominos puzzle consists of trying to place the pieces on a board, without overlapping any of
 * the pieces.  The most common version is an 8-by-8 board.  In this case, the pentominos will fill
 * 60 of the 64 avaliable squares on the board.  The squares that are to be left empty are usually
 * specified in advance.  Other board sizes are possible, including smaller boards that will not
 * hold all the pieces and some board sizes in which all the pieces will fit exactly, with no
 * extra spaces.
 * <p>In this version, the 12 pentominos are indicated by 12 different colors.  Squares that
 * are required to be left empty are colored black.  Empty squares where pentominos can be placed
 * are colored white.  When a board is first created, the black squares (if any) are chosen at
 * random, and the solution process starts immediately.  However, users can clear the board
 * and select the black squares themselves by clicking the board.
 * <p>You can use the program and the source code free of charge for any purpose in unmodified
 * form.  If you want to make a modified version and distribute it to other people, please contact
 * me about getting permission to do so.
 * <p>David J. Eck, eck@hws.edu, http://math.hws.edu/eck/, March 2006.  This program is a greatly
 * enhanced version of a similar Java program that I wrote in 1997.
 * <p>April 2006: Added Symmetry Check and One Sided features.
 */
public class PentominosPanel extends JPanel {
   
   private MosaicPanel board;  // для отображения доски с паззлами на экране
   
   private JLabel comment;   // отображение статуса внизу окна под доской
   
   private boolean[] used = new boolean[13];  //  used[i] указывает если паззл № i уже на доске

   private int numused;     // количество паззлов используемых на доске (12)
   
   private GameThread gameThread = null;   // поток в котором решается головоломка с паззлами

   private JMenuItem restartAction,restartClearAction,restartRandomAction;  // Пользовательские комманды
    private JMenuItem goAction,pauseAction,stepAction,saveAction,quitAction; 
    private JMenuItem oneSidedAction;
    private JCheckBoxMenuItem randomizePiecesChoice, checkForBlocksChoice, symmetryCheckChoice;

   private JRadioButtonMenuItem[] speedChoice = new JRadioButtonMenuItem[7];  // Menu items for setting the speed
   
   private final int[]  speedDelay = { 5, 25, 100, 500, 1000 };  // delay times between moves for speeds 2--6
   
   volatile private int selectedSpeed = 4;  // initial default speed and corresponding delay
   volatile private int delay = 100;        
   
   private boolean creatingBoard;  // this is true when user is setting up a board
   private int clickCt;  // number of squares that have been blackened by the user -- see the mousePressed routine
   
   private final static int GO_MESSAGE = 1;      // the values for the message variable   
   private final static int STEP_MESSAGE = 2;
   private final static int PAUSE_MESSAGE = 3;
   private final static int RESTART_MESSAGE = 4;
   private final static int RESTART_CLEAR_MESSAGE = 5;
   private final static int RESTART_RANDOM_MESSAGE = 6;
   private final static int TERMINATE_MESSAGE = 7;
   
   
   private int rows, cols;  // Number of rows and columns in the board.
   
   private int piecesNeeded; // How many pieces are needed to fill board as much as possible.  Always <= 12
   private int spareSpaces;  // Number of extra spaces after piecesNeeded pieces have been placed.
   
   
   private MouseListener mouseHandler = new MouseAdapter() {
      /**
       * The MousePressed routine handles slection of spaces that are to be left empty.
       * When all empty spaces have been selected, the process of finding the solution is begun.
       */
      public void mousePressed(MouseEvent evt) {
         if (creatingBoard) {
            int col = board.xCoordToColumnNumber(evt.getX());
            int row = board.yCoordToRowNumber(evt.getY());
            if (col < 0 || col >= cols || row < 0 || row >= rows)
               return;
            if (board.getColor(row,col) == null && clickCt < spareSpaces) {
               board.setColor(row,col,emptyColor);
               clickCt++;
               if (clickCt == spareSpaces)
                  comment.setText("Use \"Go\" to Start (or click a black square)");
               else
                  comment.setText("Click (up to) " + (spareSpaces-clickCt) + " squares.");
            }
            else if (board.getColor(row,col) != null && clickCt > 0){
               board.setColor(row,col,null);
               clickCt--;
               comment.setText("Click (up to) " + (spareSpaces-clickCt) + " squares.");
            }
         }
      }

   };
   
   private ActionListener menuHandler = new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
         Object source = evt.getSource();
         if (source == restartAction) {
            pauseAction.setEnabled(false);
            stepAction.setEnabled(false);
            gameThread.setMessage(RESTART_MESSAGE);
         }
         else if (source == restartClearAction) {
            pauseAction.setEnabled(false);
            stepAction.setEnabled(false);
            gameThread.setMessage(RESTART_CLEAR_MESSAGE);
         }
         else if (source == restartRandomAction) {
            pauseAction.setEnabled(false);
            stepAction.setEnabled(false);
            gameThread.setMessage(RESTART_RANDOM_MESSAGE);
         }
         else if (source == goAction) {
            pauseAction.setEnabled(true);
            stepAction.setEnabled(false);
            gameThread.setMessage(GO_MESSAGE);
         }
         else if (source == pauseAction) {
            pauseAction.setEnabled(false);
            stepAction.setEnabled(true);
            gameThread.setMessage(PAUSE_MESSAGE);
         }
         else if (source == stepAction) {
            gameThread.setMessage(STEP_MESSAGE);
         }
         else if (source == checkForBlocksChoice)
            gameThread.checkForBlocks = checkForBlocksChoice.isSelected();
         else if (source == randomizePiecesChoice)
            gameThread.randomizePieces = randomizePiecesChoice.isSelected();
         else if (source == symmetryCheckChoice)
            gameThread.symmetryCheck = symmetryCheckChoice.isSelected();
         else if (source == oneSidedAction)
            doOneSidedCommand();
         else if (source == saveAction)
            doSaveImage();
         else if (source == quitAction)
            System.exit(0);
         else if (source instanceof JRadioButtonMenuItem) {
            JRadioButtonMenuItem item = ((JRadioButtonMenuItem)source);
            int i;
            for (i = 0; i < speedChoice.length; i++) {
               if (speedChoice[i] == item)
                  break;
            }
            if (i == speedChoice.length || i == selectedSpeed)
               return;
            selectedSpeed = i;
            if (selectedSpeed < 2)
               delay = 0;
            else
               delay = speedDelay[selectedSpeed-2];
            if (gameThread.running)
               board.setAutopaint( selectedSpeed > 1 );
            board.repaint();
            gameThread.doDelay(25);
         }
      }
   };
   
   /**
    * This data structure represents the pieces.  There are 12 pieces, and each piece can be rotated
    * and flipped over.  Some of these motions leave the peice changed because of symmetry.  Each distinct 
    * position of each piece has a line in this array.  Each line has 9 elements.  The first element is
    * the number of the piece, from 1 to 12.  The remaining 8 elements describe the shape of the piece
    * in the following peculiar way:  One square is assumed to be at position (0,0) in a grid; the square is
    * chosen as the "top-left" square in the piece, in the sense that all the other squares are either to the
    * right of this square in the same row, or are in lower rows.  The remaining 4 squares in the piece are
    * encoded by 8 numbers that give the row and column of each of the remaining squares.   If the eight numbers
    * that describe the piece are (a,b,c,d,e,f,g,h) then when the piece is placed on the board with the top-left 
    * square at position (r,c), the remaining squares will be at positions (r+a,c+b), (r+c,c+d), (r+e,c+f), and
    * (r+g,c+h).  this representation is used in the putPiece() and removePiece() metthods. 
    */
   private  static final int[][] piece_data = {
      { 1, 0,1,0,2,0,3,0,4 },  // Describes piece 1 (the "I" pentomino) in its horizontal orientation.
      { 1, 1,0,2,0,3,0,4,0 },  // Describes piece 1 (the "I" pentomino) in its vertical orientation.
      { 2, 1,-1,1,0,1,1,2,0 }, // The "X" pentomino, in its only orientation.
      { 3, 0,1,1,0,2,-1,2,0 }, // etc....
      { 3, 1,0,1,1,1,2,2,2 },
      { 3, 0,1,1,1,2,1,2,2 },
      { 3, 1,-2,1,-1,1,0,2,-2 },
      { 4, 1,0,2,0,2,1,2,2 },
      { 4, 0,1,0,2,1,0,2,0 },
      { 4, 1,0,2,-2,2,-1,2,0 },
      { 4, 0,1,0,2,1,2,2,2 },
      { 5, 0,1,0,2,1,1,2,1 },
      { 5, 1,-2,1,-1,1,0,2,0 },
      { 5, 1,0,2,-1,2,0,2,1 },
      { 5, 1,0,1,1,1,2,2,0 },
      { 6, 1,0,1,1,2,1,2,2 },
      { 6, 1,-1,1,0,2,-2,2,-1 },
      { 6, 0,1,1,1,1,2,2,2 },
      { 6, 0,1,1,-1,1,0,2,-1 },
      { 7, 0,1,0,2,1,0,1,2 },
      { 7, 0,1,1,1,2,0,2,1 },
      { 7, 0,2,1,0,1,1,1,2 },
      { 7, 0,1,1,0,2,0,2,1 },
      { 8, 1,0,1,1,1,2,1,3 },
      { 8, 1,0,2,0,3,-1,3,0 },
      { 8, 0,1,0,2,0,3,1,3 },
      { 8, 0,1,1,0,2,0,3,0 },
      { 8, 0,1,1,1,2,1,3,1 },
      { 8, 0,1,0,2,0,3,1,0 },
      { 8, 1,0,2,0,3,0,3,1 },
      { 8, 1,-3,1,-2,1,-1,1,0 },
      { 9, 0,1,1,-2,1,-1,1,0 },
      { 9, 1,0,1,1,2,1,3,1 },
      { 9, 0,1,0,2,1,-1,1,0 },
      { 9, 1,0,2,0,2,1,3,1 },
      { 9, 0,1,1,1,1,2,1,3 },
      { 9, 1,0,2,-1,2,0,3,-1 },
      { 9, 0,1,0,2,1,2,1,3 },
      { 9, 1,-1,1,0,2,-1,3,-1 },
      { 10, 1,-2,1,-1,1,0,1,1 },
      { 10, 1,-1,1,0,2,0,3,0 },
      { 10, 0,1,0,2,0,3,1,1 },
      { 10, 1,0,2,0,2,1,3,0 },
      { 10, 0,1,0,2,0,3,1,2 },
      { 10, 1,0,1,1,2,0,3,0 },
      { 10, 1,-1,1,0,1,1,1,2 },
      { 10, 1,0,2,-1,2,0,3,0 },
      { 11, 1,-1,1,0,1,1,2,1 },
      { 11, 0,1,1,-1,1,0,2,0 },
      { 11, 1,0,1,1,1,2,2,1 },
      { 11, 1,0,1,1,2,-1,2,0 },
      { 11, 1,-2,1,-1,1,0,2,-1 },
      { 11, 0,1,1,1,1,2,2,1 },
      { 11, 1,-1,1,0,1,1,2,-1 },
      { 11, 1,-1,1,0,2,0,2,1 },
      { 12, 0,1,1,0,1,1,2,1 },
      { 12, 0,1,0,2,1,0,1,1 },
      { 12, 1,0,1,1,2,0,2,1 },
      { 12, 0,1,1,-1,1,0,1,1 },
      { 12, 0,1,1,0,1,1,1,2 },
      { 12, 1,-1,1,0,2,-1,2,0 },
      { 12, 0,1,0,2,1,1,1,2 },
      { 12, 0,1,1,0,1,1,2,0 }
   };
   
   private Color pieceColor[] = {  // the colors of pieces number 1 through 12; pieceColor[0] is not used.
         null,
         new Color(200,0,0),
         new Color(150,150,255),
         new Color(0,200,200),
         new Color(255,150,255),
         new Color(0,200,0),
         new Color(150,255,255),
         new Color(200,200,0),
         new Color(0,0,200),
         new Color(255,150,150),
         new Color(200,0,200),
         new Color(255,255,150),
         new Color(150,255,150)
   };
   
   private final static Color emptyColor = Color.BLACK; // the color of a square that the user has seleted to be left empty.
   
   private static final int SYMMETRY_NONE = -1;   // Possibly symmetry types of a board, used in GameThread.checkSymmetries
   private static final int SYMMETRY_V = 0;
   private static final int SYMMETRY_H = 1;
   private static final int SYMMETRY_R180 = 2;
   private static final int SYMMETRY_HV = 3;   // implies R180
   private static final int SYMMETRY_D1 = 4;
   private static final int SYMMETRY_D2 = 5;
   private static final int SYMMETRY_D1D2 = 6;  // implies R180
   private static final int SYMMETRY_R90 = 7;   // implies R180, R270
   private static final int SYMMETRY_ALL = 8;
   
   private static final int[][] remove_for_symmetry = { // By removing pieces, we elimination solutions that are just reflections/rotations of other solutions.
      { 9,10 },  // Pieces to remove for symmetry type 0 = SYMMETRY_V, etc.
      { 8,10 }, 
      { 9,10 },
      { 8,9,10 },
      { 1 },
      { 1 },
      { 12, 13, 14 },
      { 8,9,10},
      { 1,8,9,10}
   };

   private final static int[][][] side_info = { // Piece positions for the two sides of two-sided pentominos; used in implementation of "One Sided" command.
      { {27, 28, 29, 30}, {23, 24, 25, 26} }, // Sides A and B for "L" pentomino
      { {35, 36, 37, 38}, {31, 32, 33, 34} }, // for "N" pentomino
      { {43, 44, 45, 46}, {39, 40, 41, 42} }, // for "Y" pentomino
      { {47, 48, 49, 50}, {51, 52, 53, 54} }, // for "R" pentomino
      { {59, 60, 61, 62}, {55, 56, 57, 58} }, // for "P" pentomino
      { {3, 4}, {5, 6} }                      // for "Z" pentomino
   };
   
      
   /**
    * Create a pentominos board with 8 rows and 8 columns.
    */
   public PentominosPanel() {
      this(20,3,true);
   }
   
   /**
    * Create a pentominos board with a specified number of rows and columns, which must be 3 or greater.
    * If autostart is true, the program creates a random board and starts solving immediately.
    */
   public PentominosPanel(int rowCt, int colCt, boolean autostart) {
      
      setLayout(new BorderLayout(5,5));
      setBackground(Color.LIGHT_GRAY);
      
      rows = rowCt;
      if (rows < 3)
         rows = 20;
      if (cols < 3)
         cols = 3;
      cols = colCt;

      Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();
      int rowsize = (screensize.height - 100)/rows;
      if (rowsize > 35)
         rowsize = 35;  // Maximum size for squares
      else if (rowsize < 4)
         rowsize = 4;
      int colsize = (screensize.width - 50)/rows;
      if (colsize > 35)
         colsize = 35;  // Maximum size for squares
      else if (colsize < 4)
         colsize = 4;
      int size = Math.min(rowsize,colsize);
      board = new MosaicPanel(rowCt,colCt,size,size);  // for displaying the board
      board.setAlwaysDrawGrouting(true);
      board.setDefaultColor(Color.WHITE);
      board.setGroutingColor(Color.LIGHT_GRAY);
      add(board,BorderLayout.CENTER);
      
      comment = new JLabel("", JLabel.CENTER);
      comment.setFont(new Font("TimesRoman", Font.BOLD, 14));
      add(comment, BorderLayout.SOUTH);
      
      JPanel right = new JPanel();                // holds control buttons
      right.setLayout(new GridLayout(6,1,5,5));
      restartAction = new JMenuItem("Restart");
      restartClearAction = new JMenuItem("Restart / Empty Board");
      restartRandomAction = new JMenuItem("Restart / Random");
      goAction = new JMenuItem("Go");
      pauseAction = new JMenuItem("Pause");
      stepAction = new JMenuItem("Step");
      saveAction = new JMenuItem("Save Image...");
      quitAction = new JMenuItem("Quit");
      randomizePiecesChoice = new JCheckBoxMenuItem("Randomize Order of Pieces");
      checkForBlocksChoice = new JCheckBoxMenuItem("Check for Obvious Blocking");
      symmetryCheckChoice = new JCheckBoxMenuItem("Symmetry Check");
      oneSidedAction  = new JMenuItem("One Sided [Currently OFF]...");
      
      String commandKey;
      commandKey = "control ";
      try {
         String OS = System.getProperty("os.name");
         if (OS.startsWith("Mac"))
            commandKey = "meta ";
      }
      catch (Exception e) {
      }

      restartAction.addActionListener(menuHandler);
      restartClearAction.addActionListener(menuHandler);
      restartRandomAction.addActionListener(menuHandler);
      goAction.addActionListener(menuHandler);
      pauseAction.addActionListener(menuHandler);
      stepAction.addActionListener(menuHandler);
      saveAction.addActionListener(menuHandler);
      quitAction.addActionListener(menuHandler);
      randomizePiecesChoice.addActionListener(menuHandler);
      checkForBlocksChoice.addActionListener(menuHandler);
      symmetryCheckChoice.addActionListener(menuHandler);
      oneSidedAction.addActionListener(menuHandler);
      goAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "G"));
      pauseAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "P"));
      stepAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "S"));
      restartAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "R"));
      restartClearAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "E"));
      restartRandomAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "D"));
      quitAction.setAccelerator(KeyStroke.getKeyStroke(commandKey + "Q"));
      
      ButtonGroup group = new ButtonGroup();
      speedChoice[0] = new JRadioButtonMenuItem("Solutions Only / No Stop");
      speedChoice[1] = new JRadioButtonMenuItem("Very Fast (Limited Graphics)");
      speedChoice[2] = new JRadioButtonMenuItem("Faster");
      speedChoice[3] = new JRadioButtonMenuItem("Fast");
      speedChoice[4] = new JRadioButtonMenuItem("Moderate");
      speedChoice[5] = new JRadioButtonMenuItem("Slow");
      speedChoice[6] = new JRadioButtonMenuItem("Slower");
      for (int i = 0; i < 7; i++) {
         group.add(speedChoice[i]);
         speedChoice[i].addActionListener(menuHandler);
         speedChoice[i].setAccelerator(KeyStroke.getKeyStroke(commandKey + (char)('0' + i)));
      }
      speedChoice[4].setSelected(true);
      
      board.addMouseListener(mouseHandler);
      
      piecesNeeded = (rows*cols)/5;
      if (piecesNeeded > 12)
         piecesNeeded = 12;
      spareSpaces = rows*cols - 5*piecesNeeded;
      if (spareSpaces > 0)
         comment.setText("Click (up to) " + spareSpaces + " squares");
      creatingBoard = spareSpaces > 0;
      clickCt = 0;
      
      setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY,5));
      board.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY,2));
      
      gameThread = new GameThread();
      
      if (autostart) {
         gameThread.setMessage(RESTART_RANDOM_MESSAGE);
         pauseAction.setEnabled(true);
         stepAction.setEnabled(false);
         creatingBoard = false;
      }
      else {
         pauseAction.setEnabled(false);
         creatingBoard = spareSpaces > 0;
         if (creatingBoard)
            comment.setText("Select Squares or Use \"Go\" to Start");
         else
            comment.setText("Use \"Go\" to Start");
      }
      
      gameThread.start();

   }
   
   /**
    * Retrun a menu bar containing a Control menu and a Speed menu with the available commands
    * for this Pentominoes bo
    * @param includeSaveAndQuit  // If true, Save Image and Quit commands are included in the Control 
    * menu [ not suitable for use in an applet.
    */
   public JMenuBar getMenuBar(boolean includeSaveAndQuit, PentominosPanel getOptionsFromThisOne) {
      JMenuBar bar = new JMenuBar();
      JMenu control = new JMenu("Control");
      control.add(goAction);
      control.add(pauseAction);
      control.add(stepAction);
      control.addSeparator();
      control.add(restartAction);
      if (spareSpaces > 0) {
         control.add(restartClearAction);
         control.add(restartRandomAction);
      }
      control.addSeparator();
      control.add(checkForBlocksChoice);
      control.add(randomizePiecesChoice);
      if (rows*cols >= 60)
         control.add(symmetryCheckChoice);  // Only add if the board can hold all 12 pieces
      control.add(oneSidedAction);
      if (includeSaveAndQuit) {
         control.addSeparator();
         control.add(saveAction);
         control.addSeparator();
         control.add(quitAction);
      }
      bar.add(control);
      JMenu speed = new JMenu("Speed");
      speed.add(speedChoice[0]);
      speed.addSeparator();
      for (int i = 1; i < speedChoice.length; i++)
         speed.add(speedChoice[i]);
      bar.add(speed);
      if (getOptionsFromThisOne != null) {
         gameThread.randomizePieces = getOptionsFromThisOne.randomizePiecesChoice.isSelected();
         randomizePiecesChoice.setSelected(gameThread.randomizePieces);
         gameThread.checkForBlocks = (getOptionsFromThisOne.checkForBlocksChoice.isSelected());
         checkForBlocksChoice.setSelected(gameThread.checkForBlocks);
         if (rows*cols >= 60) {
            gameThread.symmetryCheck = getOptionsFromThisOne.symmetryCheckChoice.isSelected();
            symmetryCheckChoice.setSelected(gameThread.symmetryCheck);
         }
         gameThread.useOneSidedPieces = getOptionsFromThisOne.gameThread.useOneSidedPieces; 
         if (gameThread.useOneSidedPieces)
            oneSidedAction.setText("One Sided [Currently ON]...");
         gameThread.useSideA = getOptionsFromThisOne.gameThread.useSideA;
         for (int i = 0; i < speedChoice.length; i++)
            if (getOptionsFromThisOne.speedChoice[i].isSelected()) {
               speedChoice[i].setSelected(true);
               selectedSpeed = i;
               if (selectedSpeed < 2)
                  delay = 0;
               else
                  delay = speedDelay[selectedSpeed-2];
               break;
            }
      }
      return bar;
   }

   /**
    * Save a PNG image of the current board in a user-selected file.
    */
   private void doSaveImage() {
      BufferedImage image = board.getImage();  // The image currently displayed in the MosaicPanel.
      JFileChooser fileDialog = new JFileChooser();
      String defaultName = "pentominos_" + rows + "x" + cols + ".png"; // Default name for file to be saved.
      File selectedFile = new File(defaultName);
      fileDialog.setSelectedFile(selectedFile);
      fileDialog.setDialogTitle("Save Image as PNG File");
      int option = fileDialog.showSaveDialog(board);  // Presents the "Save File" dialog to the user.
      if (option != JFileChooser.APPROVE_OPTION)
         return;  // user canceled
      selectedFile = fileDialog.getSelectedFile();  // The file the user has elected to save.
      if (selectedFile.exists()) {
         int response = JOptionPane.showConfirmDialog(board,
               "The file \"" + selectedFile.getName() + "\" already exists.\nDo you want to replace it?",
               "Replace file?",
               JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
         if (response == JOptionPane.NO_OPTION)
            return; // user does not want to replace existing file
      }
      try {
         if ( ! ImageIO.write(image,"PNG",selectedFile) )  // This actually writes the image to the file.
            JOptionPane.showMessageDialog(board,"Sorry, it looks like PNG files are not supported!");
      }
      catch (Exception e) {
         JOptionPane.showMessageDialog(board,"Sorry, an error occurred while trying to save the file:\n" + e.getMessage());
      }
   }
   
   private void doOneSidedCommand() { // Called when user selects the "One Sided" command.
      final JRadioButton[][] radioButtons = new JRadioButton[6][2];
      JPanel[][] buttonPanels = new JPanel[6][2];
      boolean[] newUseSideA = gameThread.useSideA == null? new boolean[]{true,true,true,true,true,true} : (boolean[])gameThread.useSideA.clone();
      boolean newUseOneSidedPieces = gameThread.useOneSidedPieces;
      JCheckBox enableCheckBox;
      try {
         Icon icon;
         ClassLoader classLoader = getClass().getClassLoader();
         Toolkit toolkit = Toolkit.getDefaultToolkit();
         for (int i = 0; i < 6; i++) {
            ButtonGroup group = new ButtonGroup();
            for (int j = 0; j < 2; j++) {
               URL imageURL = classLoader.getResource("pics/piece" + i + "_side" + (j+1) + ".png");
               if (imageURL == null)
                  throw new Exception();
               icon = new ImageIcon(toolkit.createImage(imageURL));
               radioButtons[i][j] = new JRadioButton("");
               if (!newUseOneSidedPieces)
                  radioButtons[i][j].setEnabled(false);
               group.add(radioButtons[i][j]);
               buttonPanels[i][j] = new JPanel();
               buttonPanels[i][j].setLayout(new BorderLayout(5,5));
               buttonPanels[i][j].add(radioButtons[i][j], j == 0? BorderLayout.WEST : BorderLayout.EAST);
               JLabel label = new JLabel(icon);
               buttonPanels[i][j].add(label,BorderLayout.CENTER);
               final int k = i, l = j;
               label.addMouseListener(new MouseAdapter() {
                  public void mousePressed(MouseEvent evt) {
                     if (radioButtons[k][l].isEnabled())
                        radioButtons[k][l].setSelected(true);
                  }
               });
            }
            radioButtons[i][ newUseSideA[i]? 0 : 1 ].setSelected(true);
         }
      }
      catch (Exception e) {
         JOptionPane.showMessageDialog(null,"Internal Error!  Can't find pentomino images.\nThe \"One Sided\" command will be disabled.");
         oneSidedAction.setEnabled(false);
         e.printStackTrace();
         return;
      }
      JPanel panel = new JPanel();
      JPanel main = new JPanel();
      JPanel top = new JPanel();
      panel.setLayout(new BorderLayout(10,10));
      panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
      panel.add(main,BorderLayout.CENTER);
      panel.add(top,BorderLayout.NORTH);
      main.setLayout(new GridLayout(6,2,12,6));
      for (int i = 0; i < 6; i++) {
         main.add(buttonPanels[i][0]);
         main.add(buttonPanels[i][1]);
      }
      enableCheckBox = new JCheckBox("Enable One Sided Pieces");
      enableCheckBox.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            boolean on = ((JCheckBox)evt.getSource()).isSelected();
            for (int i = 0; i < 6; i++) {
               radioButtons[i][0].setEnabled(on);
               radioButtons[i][1].setEnabled(on);
            }
         }
      });
      enableCheckBox.setSelected(newUseOneSidedPieces);
      top.setLayout(new GridLayout(2,1,25,25));
      top.add(enableCheckBox);
      top.add(new JLabel("Select the side of each piece that you want to use:"));
      int answer = JOptionPane.showConfirmDialog(this,panel,"Use One Sided Pieces?",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
      if (answer == JOptionPane.CANCEL_OPTION)
         return;
      newUseOneSidedPieces = enableCheckBox.isSelected();
      if (!newUseOneSidedPieces) {
         oneSidedAction.setText("One Sided [Currently OFF]...");
      }
      else {
         for (int i = 0; i < 6; i++)
            newUseSideA[i] = radioButtons[i][0].isSelected();
         gameThread.useSideA = newUseSideA;
         oneSidedAction.setText("One Sided [Currently ON]...");
      }
      gameThread.useOneSidedPieces = newUseOneSidedPieces;
   }

   
   /**
    * This should be called to terminate the game playing thread just before this PentominosPanel
    * is discarded.  This is used in the frame clase, Pentominos.java.
    */
   synchronized public void terminate() {
      gameThread.setMessage(TERMINATE_MESSAGE);
      notify();
      gameThread.doDelay(25);
      board = null;
   }
   

   
   private class GameThread extends Thread {  // Этот класс запускается в потоке и решает головоломку

      int moveCount;        // Сколько ходов сделано
      int movesSinceCheck;  // Сколько ходов прошло с момента последней перерисовки доски
      int solutionCount;    // Сколько было найдено решений

      volatile boolean running;   // True когда процесс запущен и работает (и False когда приостановлен)

      boolean aborted;  // используется в play() для проверки на прекращение процесса вообще

      volatile int message = 0;  // используется потоком интерфейсной части для управление потоком решающим головоломку
      // 0 - это отсутсвие сообщений

      int[][] pieces;  // Паззлы, изначальная копия полного набора паззлов (может быть со случайным порядком)

      volatile boolean randomizePieces;  // If true, паззлы на начало решения находятся в случайном порядке
      volatile boolean checkForBlocks;   // If true, проверяет на очевидные тупики (блоки)
      volatile boolean symmetryCheck;    // If true, тогда доска проверяется на симметричность в решениях,
       // тоесть из списка удаляются некоторые паззлы, что бы избежать
      // the symmetry of the board is checked, and if it has any symmetry,
                                         // some pieces are removed from the list to avoid redundant solutions.
      volatile boolean useOneSidedPieces;// If true, only one side of two-sided pieces is used.
      
       volatile boolean[] useSideA;  // When useOneSidedPieces, this array tells which side to use for each two-sided piece.
                                     // The data for the two sides of each piece is stored in side_info.
      
      int[][] blockCheck; // используется для отмечания блокировок (собственных)
      int blockCheckCt;  // количество проверок блокировки -- используется для контроля рекурсивного подсчета
      // вместо банального использования булевого массива.
      int emptySpaces; // черные квадраты пустые, если используется

      int squaresLeftEmpty;  // квадраты которые еще остались не заполненными в решении

      boolean putPiece(int p, int row, int col) {  // пробуем вставить паззл на доску, возвращает True если подходит
         if (board.getColor(row,col) != null)
            return false;
         for (int i = 1; i < 8; i += 2) {
            if (row+pieces[p][i] < 0 || row+pieces[p][i] >= rows || col+pieces[p][i+1] < 0 || col+pieces[p][i+1] >= cols)
               return false;
            else if (board.getColor(row+pieces[p][i],col+pieces[p][i+1]) != null)  // один из нужных квадратов уже занят
               return false;
         }
         board.setColor(row,col,pieceColor[pieces[p][0]]);
         for (int i = 1; i < 8; i += 2)
            board.setColor(row + pieces[p][i], col + pieces[p][i+1], pieceColor[pieces[p][0]]);
         return true;
      }
      
      void removePiece(int p, int row, int col) { // удаляем паззл p, с позиции (row, col)
         board.setColor(row,col,null);
         for (int i = 1; i < 9; i += 2) {
            board.setColor(row + pieces[p][i], col + pieces[p][i+1], null);
         }
      }
      
      void play(int row, int col) {
         // рекурсивная функция которая пытается решить головоломку
         // усложнено только взаимодействием с интерфейсной частью напрямую
         for (int p=0; p<pieces.length; p++) {
            if (!aborted && (used[pieces[p][0]] == false)) {
               if (!putPiece(p,row,col))
                  continue;
               if (checkForBlocks && obviousBlockExists()) {
                  removePiece(p,row,col);
                  continue;
               }
               used[pieces[p][0]] = true;  // обозначаем паззл, что бы больше его не использовать
               numused++;
               moveCount++;
               movesSinceCheck++;
               boolean stepping = false;
               if (message > 0) {  // смотрим, были ли указания от интерфейсной части
                  if (message == PAUSE_MESSAGE || message == STEP_MESSAGE) {
                     stepping = true;
                     if (running && delay == 0)
                        board.forceRedraw();
                     running = false;
                     saveAction.setEnabled(true);
                     setMessage(0);
                  }
                  else if (message >=  RESTART_MESSAGE) {
                     aborted = true;
                     return;  // заметка: не устанавливать setMessage(0), так как run() должен разруливать сообщение
                  }
                  else { // сообщение к старту
                     running = true;
                     saveAction.setEnabled(false);
                     board.setAutopaint( selectedSpeed > 1 );
                     comment.setText("Solving...");
                     setMessage(0);
                  }
               }
               if (numused == piecesNeeded) {  // головоломка решена
                  solutionCount++;
                  if (delay == 0)
                     board.forceRedraw();  // board.autopaint выключен в этом случае, поэтому нужно форсировать перерисовку
                  if (selectedSpeed == 0) {
                     comment.setText("Solution #" + solutionCount + "...  (" + moveCount + " moves)");
                     doDelay(50);  // На скорости 0, просто короткая остановка, когда решение найдено.
                  }
                  else {
                     stepAction.setEnabled(true);
                     pauseAction.setEnabled(false);
                     running = false;
                     saveAction.setEnabled(true);
                     comment.setText("Solution #" + solutionCount + "  (" + moveCount + " moves)");
                     doDelay(-1);  // неопределенно длительная остановка для пользовательской команды для
                     // перезапуска решения, шага, и другое.
                     running = true;
                     board.setAutopaint( selectedSpeed > 1 );
                     saveAction.setEnabled(false);
                     comment.setText(stepping? "Paused." : "Solving...");
                  }
               }
               else {
                  if (stepping) {  // пауза после вставки паззла
                     comment.setText("Paused.");
                     if (delay == 0)
                        board.forceRedraw();
                     doDelay(-1);  // ждать неопределенно долго до появления команды
                  }
                  else if (delay > 0)
                     doDelay(delay);
                  if (movesSinceCheck >= 1000 && !stepping) {
                     if (selectedSpeed == 1) {
                        board.forceRedraw();
                        doDelay(20);
                     }
                     movesSinceCheck = 0;
                  }
                  int nextRow = row;  // поиск следующего пустого места, слева на право, а потом сверху вниз
                  int nextCol = col;
                  while (board.getColor(nextRow,nextCol) != null) { // найти следующее пустое место
                     nextCol++;
                     if (nextCol == cols) {
                        nextCol = 0;
                        nextRow++;
                        if (nextRow == row)  // выход за пределы доски
                           throw new IllegalStateException("Internal Error -- moved beyond end of board!");
                     }
                  }
                  play(nextRow, nextCol);  // попытка закончить решение
                  if (aborted)
                     return;
               }
               removePiece(p,row,col);  // [backtrack] обратный ход
               numused--;
               used[pieces[p][0]] = false;
            }
         }
         // Нельзя установить паззл на позицию (row,col), может оставим пустыми
         if (squaresLeftEmpty < emptySpaces) {
            if (aborted)
               return;
            squaresLeftEmpty++;
            int nextRow = row;  // поиск следующего пустого места, слева на право, а потом сверху вниз
            int nextCol = col;
            do { // найти следующий пустой квадрат
               nextCol++;
               if (nextCol == cols) {
                  nextCol = 0;
                  nextRow++;
                  if (nextRow == row)  // вышли за границы доски
                     return;
               }
            } while (board.getColor(nextRow,nextCol) != null);
            play(nextRow, nextCol);  // пытаемся завершить решение
            squaresLeftEmpty--;
         }
      }
      
      boolean obviousBlockExists() { // проверка на то что доска имеет участок который никогда не будет заполнен изза
          // количества квадратов в нем
         blockCheckCt++;
         int forcedEmptyCt = 0;
         for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
               int blockSize = countEmptyBlock(r,c);
               if (blockSize % 5 == 0)
                  continue;
               forcedEmptyCt += blockSize % 5;
               if (forcedEmptyCt > emptySpaces)
                  return true;
            }
         return false;
      }
      
      int countEmptyBlock(int r, int c) {  // найти размер пустого региона на доске, рекурсивный от obviousBlockExists
         if (blockCheck[r][c] == blockCheckCt || board.getColor(r,c) != null)
            return 0;
         int c1 = c, c2 = c;
         while (c1 > 0 && blockCheck[r][c1-1] < blockCheckCt && board.getColor(r,c1-1) == null)
            c1--;
         while (c2 < cols-1 && blockCheck[r][c2+1] < blockCheckCt && board.getColor(r,c2+1) == null)
            c2++;
         for (int i = c1; i <= c2; i++)
            blockCheck[r][i] = blockCheckCt;
         int ct = c2 - c1 + 1;
         if (r > 0)
            for (int i = c1; i <= c2; i++)
               ct += countEmptyBlock(r-1,i);
         if (r < rows-1)
            for (int i = c1; i <= c2; i++)
               ct += countEmptyBlock(r+1,i);
         return ct;
      }
      
      void setUpRandomBoard() { // установить случайную доску, (случайное размещение черных квадратов, тоесть пустых)
         clickCt = spareSpaces;
         board.clear();
         creatingBoard = false;
         if (spareSpaces == 0)
            return;  // паззлы заполнят доску полностью, тоесть не будет пустых мест
         int x,y;
         int placed = 0;
         int choice = (int)(3*Math.random());
         switch (choice) {
         case 0: // полностью случайно
            for (int i=0; i < spareSpaces; i ++) {
               do {
                  x = (int)(cols*Math.random());
                  y = (int)(rows*Math.random());
               } while (board.getColor(y,x) != null);
               board.setColor(y,x,emptyColor);
            }
            break;
         case 1: // симметрически случайно
            while (placed < spareSpaces) {
               x = (int)(cols*Math.random());
               y = (int)(rows*Math.random());
               if (board.getColor(y,x) == null) {
                  board.setColor(y,x,emptyColor);
                  placed++;
               }
               if (placed < spareSpaces && board.getColor(y,cols-1-x) == null) {
                  board.setColor(y,cols-1-x,emptyColor);
                  placed++;
               }
               if (placed < spareSpaces && board.getColor(rows-1-y,x) == null) {
                  board.setColor(rows-1-y,x,emptyColor);
                  placed++;
               }
               if (placed < spareSpaces && board.getColor(rows-1-y,cols-1-x) == null) {
                  board.setColor(rows-1-y,cols-1-x,emptyColor);
                  placed++;
               }
            }
            break;
         default: // случайный блок
            int blockrows;
         int blockcols;
         if (spareSpaces < 4) {
            blockrows = 1;
            blockcols = spareSpaces;
         }
         else if (spareSpaces == 4) {
            blockrows = 2;
            blockcols = 2;
         }
         else {
            blockcols = (int)Math.sqrt(spareSpaces);
            if (blockcols > cols)
               blockcols = cols;
            blockrows = spareSpaces / blockcols;
            if (blockrows*blockcols < spareSpaces)
               blockrows++;
         }
         x = (int)((cols - blockcols+ 1)*Math.random());
         y = (int)((rows - blockrows + 1)*Math.random());
         for (int r = 0; r < blockrows; r++)
            for (int c = 0; c < blockcols && placed < spareSpaces; c++) {
               board.setColor(y+r,x+c,emptyColor);
               placed++;
            }
         break;
         }
      }
      
      private int checkSymmetries(boolean allowFlip) {  //вернуть код типа симметрии для доски
         boolean H, V, D1, D2, R90, R180;
         boolean[][] empty = new boolean[rows][cols];
         for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
               empty[i][j] = (board.getColor(i,j) == null);
         if (!allowFlip)
            V = false;
         else {
            V = true;
            VLOOP: for (int i = 0; i < rows; i++)
               for (int j = 0; j < cols/2; j++)
                  if (empty[i][j] != empty[i][cols-j-1]) {
                     V = false;
                     break VLOOP;
                  }
         }
         if (rows != cols)
            R90 = false;
         else {
            R90 = true;
            R90LOOP: for (int i = 0; i < rows-1; i++)
               for (int j = 0; j < cols; j++)
                  if (empty[i][j] != empty[rows-j-1][i]) {
                     R90 = false;
                     break R90LOOP;
                  }
         }
         if (R90) { // если симметрично для поворота на 90-градусов, только возможности 8-кратного поворота
         // или полностью вращаемая симметрия
            if (V)
               return SYMMETRY_ALL;
            else
               return SYMMETRY_R90;
         }
         if (!allowFlip)
            H = false;
         else {
            H = true;
            HLOOP: for (int i = 0; i < rows/2; i++)
               for (int j = 0; j < cols; j++)
                  if (empty[i][j] != empty[rows-i-1][j]) {
                     H = false;
                     break HLOOP;
                  }
         }
         R180 = true;
         R180LOOP: for (int i = 0; i < rows; i++)
            for (int j = 0; j < (cols+1)/2; j++)
               if (empty[i][j] != empty[rows-i-1][cols-j-1]) {
                  R180 = false;
                  break R180LOOP;
               }
         if (!allowFlip || (rows != cols)) 
            D1 = D2 = false;
         else {
            D1 = true;
            D1LOOP: for (int i = 1; i < rows; i++)
               for (int j = 0; j < i; j++)
                  if (empty[i][j] != empty[j][i]) {
                     D1 = false;
                     break D1LOOP;
                  }
            D2 = true;
            D2LOOP: for (int i = 0; i < rows-1; i++)
               for (int j = 0; j < rows-i-1; j++)
                  if (empty[i][j] != empty[rows-j-1][rows-i-1]) {
                     D2 = false;
                     break D2LOOP;
                  }
         }
         if (D1) { // не может иметь H или V, так как R90 = true
            if (D2)
               return SYMMETRY_D1D2;
            else
               return SYMMETRY_D1;
         }
         else if (H) { // не может иметь D2, так как R90 = true
            if (V)
               return SYMMETRY_HV;
            else
               return SYMMETRY_H;
         }
         else if (D2)
            return SYMMETRY_D2;
         else if (V)
            return SYMMETRY_V;
         else if (R180)
            return SYMMETRY_R180;
         else
            return SYMMETRY_NONE;
      }
      
      synchronized void doDelay(int milliseconds) {
          // ожидает указанное время, или пока не прервет управляющее сообщение присланное через setMessage()
          // для неопределенно длительного ожидания < 0
         if (milliseconds < 0) {
            try {
               wait();
            }
            catch (InterruptedException e) {
            }
         }
         else {
            try {
               wait(milliseconds);
            }
            catch (InterruptedException e) {
            }
         }
      }
      
      
      synchronized void setMessage(int message) {  // отправить сообщение в GameThread
         this.message = message;
         if (message > 0)
            notify();  // разбедить GameThread если он спит или ожидает сообщения (в doDelay)
      }

      
      /**
       * Запускает игру
       */
      public void run() { 
         while (true) {
            try {
               running = false;
               saveAction.setEnabled(true);
               board.repaint();
               while (message != GO_MESSAGE && message != TERMINATE_MESSAGE) {
               // ждем пока установятся настройки программы
                  if (message == RESTART_RANDOM_MESSAGE) {
                     setUpRandomBoard();
                     comment.setText("Solving...");
                     creatingBoard = false;
                     setMessage(GO_MESSAGE);
                     doDelay(1000);
                  }
                  else if (message == RESTART_CLEAR_MESSAGE || message == RESTART_MESSAGE) {
                     clickCt = 0;
                     creatingBoard = spareSpaces > 0;
                     if (message == RESTART_MESSAGE && spareSpaces > 0) {
                        for (int r = 0; r < rows; r++)
                           for (int c = 0; c < cols; c++)
                              if (board.getColor(r,c) != emptyColor)
                                 board.setColor(r,c,null);
                              else
                                 clickCt++;
                        if (spareSpaces > 0 && clickCt == spareSpaces)
                           comment.setText("Use \"Go\" to Start (or click a black square)");
                        else
                           comment.setText("Select Squares or Use \"Go\" to Start");
                     }
                     else {
                        board.clear();
                        if (creatingBoard)
                           comment.setText("Click (up to) " + spareSpaces + " squares");
                        else
                           comment.setText("Use \"Go\" to Start");
                     }
                     setMessage(0);
                     doDelay(-1);  // ждать навсегда (для управлением старта программы)
                  }
               }
               if (message == TERMINATE_MESSAGE)
                  break;
               creatingBoard = false;
               running = true;
               saveAction.setEnabled(false);
               board.setAutopaint(delay > 0);
               board.repaint();
               doDelay(25);
               // begin next game
               pauseAction.setEnabled(true);
               stepAction.setEnabled(false);
               comment.setText("Solving...");
               message = 0;
               for (int i=1; i<=12; i++)
                  used[i] = false;
               numused = 0;
               int startRow = 0;  // левый верхний угол
               int startCol = 0;
               while (board.getColor(startRow,startCol) != null) {
                  startCol++;  // move past any filled squares, since Play(square) assumes the square is empty
                  if (startCol == cols) {
                     startCol = 0;
                     startRow++;
                  }
               }
               moveCount = movesSinceCheck = solutionCount = 0;
               int[][] pieces2use = piece_data;
               if (symmetryCheck || useOneSidedPieces) {
                  long removeMask = 0;
                  if (symmetryCheck) {
                     int symmetryType = checkSymmetries(!useOneSidedPieces);
                     System.out.println("Found symmetry type " + symmetryType);
                     if (symmetryType != SYMMETRY_NONE) {
                        for (int p = 0; p < remove_for_symmetry[symmetryType].length; p++)
                           removeMask = removeMask | (1L << remove_for_symmetry[symmetryType][p]);
                     }
                  }
                  if (useOneSidedPieces) {
                     for (int p = 0; p < 6; p++) {
                        int[] remove_for_one_sided = side_info[p][ useSideA[p]? 1 : 0 ];
                        for (int j = 0; j < remove_for_one_sided.length; j++)
                           removeMask = removeMask | (1L << remove_for_one_sided[j]);
                     }
                  }
                  if (removeMask != 0) {
                     int ct = 0;
                     for (int p = 0; p < 63; p++)
                        if ((removeMask & (1L << p)) != 0)
                           ct++;
                     pieces2use = new int[63-ct][];
                     int j = 0;
                     System.out.print("Remove piece ");
                     for (int p = 0; p < piece_data.length; p++)
                        if ((removeMask & (1L << p)) == 0)
                           pieces2use[j++] = piece_data[p];
                        else
                           System.out.print(p + " ");
                     System.out.println("\n");
                  }
               }
               pieces = pieces2use;
               if (randomizePieces) {
                  if (pieces2use == piece_data) {  // Не мешать порядок паззлов в исходном массиве
                     pieces = new int[pieces2use.length][];
                     for (int i = 0; i < pieces.length; i++)
                        pieces[i] = pieces2use[i];
                  }
                  for (int i = 0; i < pieces.length; i++) {
                     int r = (int)(pieces.length * Math.random());
                     int[] temp = pieces[r];
                     pieces[r] = pieces[i];
                     pieces[i] = temp;
                  }
               }
               board.setAutopaint( selectedSpeed > 1 );
               randomizePiecesChoice.setEnabled(false);
               symmetryCheckChoice.setEnabled(false);
               oneSidedAction.setEnabled(false);
               blockCheck = new int[rows][cols];
               blockCheckCt = 0;
               emptySpaces = spareSpaces - clickCt;
               squaresLeftEmpty = 0;
               aborted = false;
               boolean blocked = false;
               if (checkForBlocks && obviousBlockExists())
                  blocked = true;
               else
                  play(startRow,startCol);   // run the recursive algorithm that will solve the puzzle
               if (message == TERMINATE_MESSAGE)
                  break;
               randomizePiecesChoice.setEnabled(true);
               symmetryCheckChoice.setEnabled(true);
               oneSidedAction.setEnabled(true);
               running = false;
               saveAction.setEnabled(true);
               board.setAutopaint(true);
               board.repaint();
               if (!aborted) {
                  pauseAction.setEnabled(false);
                  stepAction.setEnabled(false);
                  if (blocked)
                     comment.setText("Unsolvable because of obvious blocking.");
                  else if (solutionCount == 0)
                     comment.setText("Done. No soutions. " + moveCount + " moves.");
                  else if (solutionCount == 1)
                     comment.setText("Done. 1 solution. " + moveCount + " moves.");
                  else
                     comment.setText("Done. " + solutionCount + " solutions. "+ moveCount + " moves.");
                  if (spareSpaces > 0)
                     creatingBoard = true;
                  doDelay(-1);
               }
               if (message == TERMINATE_MESSAGE)
                  break;
            }
            catch (Exception e) {
               JOptionPane.showMessageDialog(PentominosPanel.this,"An internal error has occurred:\n"+ e + "\n\nRESTARTING.");
               e.printStackTrace();
               board.setAutopaint(true);
               pauseAction.setEnabled(true);
               stepAction.setEnabled(false);
               message = RESTART_MESSAGE;
            }
         } // end while
      }
      
   } // end nested class GameThread
   
   
}

