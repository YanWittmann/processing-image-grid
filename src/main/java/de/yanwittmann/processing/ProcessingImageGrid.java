package de.yanwittmann.processing;

import controlP5.ControlEvent;
import controlP5.ControlP5;
import controlP5.Group;
import processing.core.PApplet;
import processing.core.PImage;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This sketch creates an interactive image grid that applies various visual effects based on user input and noise functions.
 */
public class ProcessingImageGrid extends PApplet {

    // Directories for grid images and input images
    private final String gridImagesDir = "data/grid-elements";
    private final String inputImagesDir = "data/images";

    // Standby mode, -1 for disabled, otherwise the amount of seconds to automatically switch images
    private final int standbySwitchSeconds = 60 * 2;

    // Input images and grid images
    private PImage[] inputImages;
    private PImage[] gridImages;
    private float[] gridBrightness;
    private final TreeMap<Float, PImage> brightnessToImageMap = new TreeMap<>();

    // Current and last images
    private int currentImageIndex = 0;
    private PImage currentImage;
    private PImage lastImage;
    private PImage scaledImage;

    // Grid settings
    private final int gridSize = 25;
    private int targetWidth = 1500; // default size only, customizable in setup
    private int targetHeight = 900;
    private int cols, rows;
    private GridCell[][] gridCells;
    private int offsetX;
    private int offsetY;

    // Noise settings
    private float noiseScale = 0.03f;
    private float noiseTimeScale = 0.2f;

    // Update probabilities
    private float minUpdateProbability = 0.01f;
    private float maxUpdateProbability = 0.05f;

    // Neighbor influence settings
    private int neighborRadius = 1;
    private float neighborCount = pow(neighborRadius * 2 + 1, 2) - 1;
    private float influenceIncrement = 18f / neighborCount;

    // Special effect settings
    private int specialEffectType = 0;
    private float specialEffectInfluenceRadiusLow = 0;
    private float specialEffectInfluenceRadiusHigh = 3;
    private float setLastImageInfluence = 1f;
    private float lastImageInfluenceReductionChance = 0.5f;
    private float lastImageInfluenceReductionLow = 0.0f;
    private float lastImageInfluenceReductionHigh = 0.3f;
    private float specialEffectDisplacementStrength = 10f;

    // Global displacement settings
    private int globalDisplacementType = -1;

    // Debug settings
    private boolean debugVisualizeNoise = false;

    // ControlP5 UI
    ControlP5 cp5;
    boolean showUI = true;

    public static void main(final String[] args) {
        PApplet.main(ProcessingImageGrid.class);
    }

    @Override
    public void settings() {
        // Ensure size is a multiple of gridSize
        targetWidth -= targetWidth % gridSize;
        targetHeight -= targetHeight % gridSize;
        size(targetWidth, targetHeight);
    }

    int state = 0;

    @Override
    public void setup() {
        surface.setResizable(true);
        background(0);

        cp5 = new ControlP5(this);

        // Create UI groups for organization
        Group noiseGroup = cp5.addGroup("Noise Settings")
                .setPosition(10, 15)
                .setWidth(220)
                .setBackgroundColor(color(0, 50));

        Group updateProbabilityGroup = cp5.addGroup("Update Probability")
                .setPosition(10, 120)
                .setWidth(220)
                .setBackgroundColor(color(0, 50));

        Group specialEffectGroup = cp5.addGroup("Special Effects")
                .setPosition(10, 300)
                .setWidth(220)
                .setBackgroundColor(color(0, 50));

        Group globalDisplacementGroup = cp5.addGroup("Global Displacement")
                .setPosition(10, 610)
                .setWidth(220)
                .setBackgroundColor(color(0, 50));

        // Noise Settings
        cp5.addSlider("noiseScale")
                .setPosition(10, 20)
                .setSize(200, 20)
                .setRange(0.0f, 0.1f)
                .setValue(noiseScale)
                .setLabel("Noise Scale")
                .moveTo(noiseGroup);

        cp5.addSlider("noiseTimeScale")
                .setPosition(10, 50)
                .setSize(200, 20)
                .setRange(0.0f, 1.0f)
                .setValue(noiseTimeScale)
                .setLabel("Noise Time Scale")
                .moveTo(noiseGroup);

        // Update Probability Settings
        cp5.addSlider("minUpdateProbability")
                .setPosition(10, 20)
                .setSize(200, 20)
                .setRange(0.0f, 0.05f)
                .setValue(minUpdateProbability)
                .setLabel("Min Update Probability")
                .moveTo(updateProbabilityGroup);

        cp5.addSlider("maxUpdateProbability")
                .setPosition(10, 50)
                .setSize(200, 20)
                .setRange(0.0f, 0.1f)
                .setValue(maxUpdateProbability)
                .setLabel("Max Update Probability")
                .moveTo(updateProbabilityGroup);

        // Neighbor Settings
        cp5.addSlider("neighborRadius")
                .setPosition(10, 80)
                .setSize(200, 20)
                .setRange(1, 5)
                .setNumberOfTickMarks(5)
                .setValue(neighborRadius)
                .setLabel("Neighbor Radius")
                .snapToTickMarks(true)
                .moveTo(updateProbabilityGroup);

        // Special Effects Settings
        cp5.addSlider("specialEffectType")
                .setPosition(10, 20)
                .setSize(200, 20)
                .setRange(-1, 3)
                .setNumberOfTickMarks(5)
                .setValue(specialEffectType)
                .setLabel("Special Effect Type")
                .snapToTickMarks(true)
                .moveTo(specialEffectGroup);

        cp5.addSlider("specialEffectInfluenceRadiusLow")
                .setPosition(10, 50)
                .setSize(200, 20)
                .setRange(0, 10)
                .setValue(specialEffectInfluenceRadiusLow)
                .setLabel("Effect Radius Low")
                .moveTo(specialEffectGroup);

        cp5.addSlider("specialEffectInfluenceRadiusHigh")
                .setPosition(10, 80)
                .setSize(200, 20)
                .setRange(0, 10)
                .setValue(specialEffectInfluenceRadiusHigh)
                .setLabel("Effect Radius High")
                .moveTo(specialEffectGroup);

        cp5.addSlider("setLastImageInfluence")
                .setPosition(10, 110)
                .setSize(200, 20)
                .setRange(0.0f, 1.0f)
                .setValue(setLastImageInfluence)
                .setLabel("[LI] Last Image Set Influence")
                .moveTo(specialEffectGroup);

        cp5.addSlider("lastImageInfluenceReductionChance")
                .setPosition(10, 140)
                .setSize(200, 20)
                .setRange(0.0f, 1.0f)
                .setValue(lastImageInfluenceReductionChance)
                .setLabel("[LI] Influence Reduction Chance")
                .moveTo(specialEffectGroup);

        cp5.addSlider("lastImageInfluenceReductionLow")
                .setPosition(10, 170)
                .setSize(200, 20)
                .setRange(0.0f, 0.5f)
                .setValue(lastImageInfluenceReductionLow)
                .setLabel("[LI] Influence Reduction Low")
                .moveTo(specialEffectGroup);

        cp5.addSlider("lastImageInfluenceReductionHigh")
                .setPosition(10, 200)
                .setSize(200, 20)
                .setRange(0.0f, 0.5f)
                .setValue(lastImageInfluenceReductionHigh)
                .setLabel("[LI] Influence Reduction High")
                .moveTo(specialEffectGroup);

        cp5.addSlider("specialEffectDisplacementStrength")
                .setPosition(10, 230)
                .setSize(200, 20)
                .setRange(0.0f, 30f)
                .setValue(specialEffectDisplacementStrength)
                .setLabel("[DI] Displacement Strength")
                .moveTo(specialEffectGroup);

        cp5.addToggle("debugVisualizeNoise")
                .setPosition(10, 260)
                .setSize(50, 20)
                .setValue(debugVisualizeNoise)
                .setLabel("Debug Visualize Noise")
                .moveTo(specialEffectGroup);

        // Global Displacement Settings
        cp5.addSlider("globalDisplacementType")
                .setPosition(10, 20)
                .setSize(200, 20)
                .setRange(0, 2)
                .setNumberOfTickMarks(3)
                .setValue(globalDisplacementType)
                .setLabel("Displacement Type")
                .snapToTickMarks(true)
                .moveTo(globalDisplacementGroup);

        // Timer to periodically change the current image
        if (standbySwitchSeconds > 0) {
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    if (inputImages == null) {
                        return;
                    }
                    do {
                        currentImageIndex = (currentImageIndex + 1) % inputImages.length;
                    } while (inputImages[currentImageIndex] == null);
                    updateCurrentImage();
                }
            }, 0, TimeUnit.SECONDS.toMillis(standbySwitchSeconds));
        }
    }

    public void controlEvent(ControlEvent theEvent) {
        if (theEvent.isFrom("neighborRadius")) {
            neighborCount = pow(neighborRadius * 2 + 1, 2) - 1;
            influenceIncrement = 18f / neighborCount;
        }
    }

    int lastMouseX = -1;
    int lastMouseY = -1;

    @Override
    public void draw() {
        if (lastMouseX == -1 || lastMouseY == -1) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        if (state != 2) {
            if (state == 1) {
                state = 2;

                surface.setResizable(false);
                targetWidth = width;
                targetHeight = height;
                targetWidth -= targetWidth % gridSize;
                targetHeight -= targetHeight % gridSize;
                surface.setSize(targetWidth, targetHeight);

                loadGridImages();
                println("Loaded " + gridImages.length + " grid images");
                loadInputImages();
                println("Loaded " + inputImages.length + " input images");
                prepareGrid();
                println("Prepared grid with " + cols + "x" + rows + " cells");
                updateCurrentImage();
                println("Processed selected image");

                background(0);
            }
            return;
        }

        updateGrid();
        renderGrid();

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public void keyPressed() {
        if (keyCode == LEFT) {
            do {
                currentImageIndex = (currentImageIndex - 1 + inputImages.length) % inputImages.length;
            } while (inputImages[currentImageIndex] == null);
            updateCurrentImage();
        } else if (keyCode == RIGHT) {
            do {
                currentImageIndex = (currentImageIndex + 1) % inputImages.length;
            } while (inputImages[currentImageIndex] == null);
            updateCurrentImage();
        } else if (key == 'n') {
            debugVisualizeNoise = !debugVisualizeNoise;
        } else if (key == 's') {
            saveFrame("output.png");
        } else if (key == ' ') {
            state = 1;
        } else if (key == 'u') {
            showUI = !showUI;
            cp5.setVisible(showUI);
        } else if (key >= '1' && key <= '9') {
            int setIndex = key - '1' + 1;

            // Restore default values
            noiseScale = 0.03f;
            noiseTimeScale = 0.2f;
            minUpdateProbability = 0.01f;
            maxUpdateProbability = 0.05f;
            neighborRadius = 1;
            neighborCount = pow(neighborRadius * 2 + 1, 2) - 1;
            influenceIncrement = 18f / neighborCount;

            specialEffectType = 1;
            specialEffectInfluenceRadiusLow = 0;
            specialEffectInfluenceRadiusHigh = 3;

            setLastImageInfluence = 1f;
            lastImageInfluenceReductionChance = 0.5f;
            lastImageInfluenceReductionLow = 0.0f;
            lastImageInfluenceReductionHigh = 0.3f;

            if (setIndex == 2) {
                minUpdateProbability = 0.00f;
                maxUpdateProbability = 0.04f;
                neighborRadius = 2;
                neighborCount = pow(neighborRadius * 2 + 1, 2) - 1;
                influenceIncrement = 9f / neighborCount;
            } else if (setIndex == 3) {
                minUpdateProbability = 0.00f;
                maxUpdateProbability = 0.04f;
                neighborRadius = 3;
                neighborCount = pow(neighborRadius * 2 + 1, 2) - 1;
                influenceIncrement = 9f / neighborCount;
            } else if (setIndex == 4) {
                specialEffectType = 2;
                specialEffectInfluenceRadiusLow = 3;
                specialEffectInfluenceRadiusHigh = 8;
            } else if (setIndex == 5) {
                specialEffectType = 3;
                specialEffectInfluenceRadiusLow = 3;
                specialEffectInfluenceRadiusHigh = 8;
            } else if (setIndex == 6) {
                specialEffectType = -1;
            }

            println("Set " + setIndex);
        }
    }

    // Supported image file extensions
    final Set<String> imageExtensions = Set.of("jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "wbmp");

    /**
     * Loads grid images from the specified directory.
     */
    private void loadGridImages() {
        final File dir = new File(sketchPath(gridImagesDir));
        final File[] files = dir.listFiles();
        if (files == null) {
            println("No grid images found in directory: " + gridImagesDir);
            exit();
            return;
        }
        final int numImages = files.length;
        gridImages = new PImage[numImages];
        gridBrightness = new float[numImages];
        for (int i = 0; i < numImages; i++) {
            if (!files[i].isFile()) {
                continue;
            }
            final String extension = files[i].getName().substring(files[i].getName().lastIndexOf('.') + 1);
            if (!imageExtensions.contains(extension)) {
                continue;
            }
            final PImage img = loadImage(files[i].getAbsolutePath());
            gridImages[i] = img;
            final float brightness = calculateAverageBrightness(img);
            gridBrightness[i] = brightness;
            brightnessToImageMap.put(brightness, img);
        }
    }

    /**
     * Loads input images from the specified directory.
     */
    private void loadInputImages() {
        final File dir = new File(sketchPath(inputImagesDir));
        File[] files = dir.listFiles();
        if (files == null) {
            println("No input images found in directory: " + inputImagesDir);
            exit();
            return;
        }
        final File[] finalFiles = Arrays.stream(files).filter(File::isFile).sorted(Comparator.comparing(File::getName)).toArray(File[]::new);
        final int numImages = files.length;
        inputImages = new PImage[numImages];
        println("Loading " + numImages + " files in " + inputImagesDir);

        final AtomicBoolean failed = new AtomicBoolean(false);
        try {
            final ExecutorService executor = Executors.newFixedThreadPool(8);

            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < numImages; i++) {
                if (!files[i].isFile()) {
                    continue;
                }
                final String extension = files[i].getName().substring(files[i].getName().lastIndexOf('.') + 1);
                if (!imageExtensions.contains(extension)) {
                    continue;
                }
                final int index = i;
                futures.add(executor.submit(() -> {
                    final PImage img = loadImage(finalFiles[index].getAbsolutePath());
                    inputImages[index] = img;
                    if (index % 10 == 0) {
                        println("Loaded " + index + " / " + numImages + " files");
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(); // Wait for all tasks to complete
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            failed.set(true);
        }

        if (failed.get()) {
            println("Failed to load input images");
            exit();
        }
    }

    /**
     * Prepares the grid cells based on the grid size.
     */
    private void prepareGrid() {
        cols = width / gridSize;
        rows = height / gridSize;
        gridCells = new GridCell[cols][rows];
        // Initialize grid cells
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                gridCells[x][y] = new GridCell();
                gridCells[x][y].x = x;
                gridCells[x][y].y = y;
            }
        }
    }

    /**
     * Updates the current image and scales it appropriately.
     */
    private void updateCurrentImage() {
        lastImage = scaledImage;
        currentImage = inputImages[currentImageIndex];

        final float aspectRatio = (float) currentImage.width / currentImage.height;
        final int maxCols = cols;
        final int maxRows = rows;
        int newCols, newRows;

        if ((float) maxCols / maxRows > aspectRatio) {
            // Grid is wider than image
            newRows = maxRows;
            newCols = (int) (newRows * aspectRatio);
        } else {
            // Grid is taller than image
            newCols = maxCols;
            newRows = (int) (newCols / aspectRatio);
        }

        scaledImage = createImage(newCols, newRows, RGB);
        currentImage.resize(newCols, newRows);
        scaledImage.copy(currentImage, 0, 0, currentImage.width, currentImage.height, 0, 0, newCols, newRows);

        // Position offsets to center the image
        offsetX = (cols - newCols) / 2;
        offsetY = (rows - newRows) / 2;

        if (lastImage == null) {
            lastImage = scaledImage;
        }
    }

    /**
     * Finds the grid cells that are hovered by the mouse with a given padding radius.
     */
    private List<GridCell> findHoveredGridCells(int paddingRadius) {
        final List<GridCell> hoveredCells = new ArrayList<>();
        int mouseXGrid = mouseX / gridSize;
        int mouseYGrid = mouseY / gridSize;

        for (int dx = -paddingRadius; dx <= paddingRadius; dx++) {
            for (int dy = -paddingRadius; dy <= paddingRadius; dy++) {
                int nx = mouseXGrid + dx;
                int ny = mouseYGrid + dy;
                if (nx >= 0 && nx < cols && ny >= 0 && ny < rows && (dx * dx + dy * dy <= paddingRadius * paddingRadius)) {
                    hoveredCells.add(gridCells[nx][ny]);
                }
            }
        }
        return hoveredCells;
    }

    /**
     * Updates the grid based on noise and user interactions.
     */
    private void updateGrid() {
        final float time = millis() / 1000.0f; // seconds

        boolean[][] shouldUpdate = new boolean[cols][rows];
        float[][] probabilities = new float[cols][rows];
        float[][] neighborAdjustment = new float[cols][rows];
        float[][] randomValues = new float[cols][rows];

        // Prepare values for the entire grid
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                randomValues[x][y] = random(1);
                neighborAdjustment[x][y] = 0;
            }
        }

        // First Pass: Initial Update Decision
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                // Compute noise-based update probability
                final float noiseValue = noise(x * noiseScale, y * noiseScale, time * noiseTimeScale);
                final float adjustedNoiseValue = 1 / (1 + exp(-10 * (noiseValue - 0.5f)));
                probabilities[x][y] = adjustedNoiseValue * (maxUpdateProbability - minUpdateProbability) + minUpdateProbability;

                shouldUpdate[x][y] = randomValues[x][y] < probabilities[x][y];
            }
        }

        if (debugVisualizeNoise) {
            for (int x = 0; x < cols; x++) {
                for (int y = 0; y < rows; y++) {
                    final float displayValue = 255 * map(probabilities[x][y], minUpdateProbability, maxUpdateProbability, 0, 1);
                    gridCells[x][y].image = getClosestGridImage(displayValue);
                    gridCells[x][y].color = color(displayValue);
                    gridCells[x][y].dirty = true;
                }
            }
            return;
        }

        // Second Pass: Influence Neighbors
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                final float adjustmentValue = shouldUpdate[x][y] ? influenceIncrement : -influenceIncrement / neighborCount;

                for (int dx = -neighborRadius; dx <= neighborRadius; dx++) {
                    for (int dy = -neighborRadius; dy <= neighborRadius; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < cols && ny >= 0 && ny < rows) {
                            if (!(dx == 0 && dy == 0)) { // Exclude the cell itself
                                neighborAdjustment[nx][ny] += adjustmentValue;
                            }
                        }
                    }
                }
            }
        }

        // Third Pass: Second Update Attempt
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (!shouldUpdate[x][y]) {
                    // Add the increased probability from neighbor influence
                    float adjustedProbability = constrain((neighborAdjustment[x][y] + probabilities[x][y]) * 1.7f, 0, 1);

                    // Re-attempt to update
                    shouldUpdate[x][y] = randomValues[x][y] < adjustedProbability;
                }
            }
        }

        // Special effect: Apply effects based on mouse interaction
        final List<GridCell> hoveredGridCells = findHoveredGridCells((int) map(dist(mouseX, mouseY, lastMouseX, lastMouseY), 0, 100, specialEffectInfluenceRadiusLow, specialEffectInfluenceRadiusHigh));
        for (final GridCell cell : hoveredGridCells) {
            if (specialEffectType == 1 || specialEffectType == -1) {
                // Variant 1: Apply last image influence based on mouse movement
                cell.lastImageInfluence = setLastImageInfluence;
                if (randomValues[cell.x][cell.y] > 0.3) {
                    cell.color = lastImage.get(cell.x - offsetX, cell.y - offsetY);
                    cell.dirty = true;
                }
            }

            // Variant 2: Apply displacement based on the mouse direction
            if (specialEffectType == 2 || specialEffectType == 3 || specialEffectType == -1) {
                final int dx = mouseX - lastMouseX;
                final int dy = mouseY - lastMouseY;
                final float distance = dist(mouseX, mouseY, lastMouseX, lastMouseY);
                if (distance != 0 && distance < Math.min(displayWidth, displayHeight) - 50) {
                    if (specialEffectType == 3) {
                        cell.displacementX += -dx / distance * specialEffectDisplacementStrength;
                        cell.displacementY += -dy / distance * specialEffectDisplacementStrength;
                    } else {
                        cell.displacementX = -dx / distance * specialEffectDisplacementStrength;
                        cell.displacementY = -dy / distance * specialEffectDisplacementStrength;
                    }

                    final float nx = dx / distance;
                    final float ny = dy / distance;
                    final float displacement = map(dist(mouseX, mouseY, cell.x * gridSize, cell.y * gridSize), 0, 100, 0, 1);
                    cell.displacementX += nx * displacement * 5;
                    cell.displacementY += ny * displacement * 5;

                    cell.dirty = true;
                }
            }
        }

        // Global displacement effect
        if (globalDisplacementType != 0) {
            for (int x = 0; x < cols; x++) {
                for (int y = 0; y < rows; y++) {
                    if (globalDisplacementType == 1 || globalDisplacementType == 2) {
                        final float noiseValue = noise(x * noiseScale, y * noiseScale, time * noiseTimeScale);
                        final float nx = noise(x * noiseScale, y * noiseScale, time * noiseTimeScale + 100);
                        final float ny = noise(x * noiseScale, y * noiseScale, time * noiseTimeScale + 200);
                        final float displacement = map(noiseValue, 0, 1, 0, 1);

                        if (globalDisplacementType == 1) {
                            gridCells[x][y].displacementX = nx * displacement * 3;
                            gridCells[x][y].displacementY = ny * displacement * 3;
                        } else if (globalDisplacementType == 2) {
                            gridCells[x][y].displacementX += nx * displacement * 0.1f;
                            gridCells[x][y].displacementY += ny * displacement * 0.1f;
                        }
                    }
                }
            }
        }

        // Final Pass: Apply Updates
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (shouldUpdate[x][y]) {
                    gridCells[x][y].dirty = true;

                    final int imgX = x - offsetX + (int) gridCells[x][y].displacementX;
                    final int imgY = y - offsetY + (int) gridCells[x][y].displacementY;

                    if (gridCells[x][y].displacementX != 0 || gridCells[x][y].displacementY != 0) {
                        gridCells[x][y].displacementX -= gridCells[x][y].displacementX > 0 ? 1 : -1;
                        gridCells[x][y].displacementY -= gridCells[x][y].displacementY > 0 ? 1 : -1;
                    }

                    final int targetColor;
                    if (gridCells[x][y].lastImageInfluence > 0) {
                        targetColor = blendColor(
                                getPixelColor(scaledImage, imgX, imgY, probabilities),
                                getPixelColor(lastImage, imgX, imgY, probabilities),
                                gridCells[x][y].lastImageInfluence);
                        gridCells[x][y].lastImageInfluence -= random(1) > lastImageInfluenceReductionChance
                                ? lastImageInfluenceReductionHigh : lastImageInfluenceReductionLow;
                    } else {
                        targetColor = getPixelColor(scaledImage, imgX, imgY, probabilities);
                    }

                    final float brightnessValue = brightness(targetColor);
                    final float targetBrightness = modulateBrightness(brightnessValue);

                    blendColorOnGridElement(x, y, targetBrightness, targetColor, randomValues[x][y] > 0.3 ? 0.7f : 0.3f);
                }
            }
        }
    }

    /**
     * Retrieves the pixel color from an image, adjusting for out-of-bounds coordinates.
     */
    private int getPixelColor(final PImage img, final int x, final int y, float[][] probabilities) {
        if (x >= 0 && x < img.width && y >= 0 && y < img.height) {
            return img.get(x, y);
        }

        // Outside of image bounds, adjust target color: blend border pixel with a gray tone
        int sampleX = x;
        int sampleY = y;

        if (x < 0) {
            sampleX = 0;
        } else if (x >= img.width) {
            sampleX = img.width - 1;
        }

        if (y < 0) {
            sampleY = 0;
        } else if (y >= img.height) {
            sampleY = img.height - 1;
        }

        final int borderColor = img.get(sampleX, sampleY);
        final int grayTone = color(0 + map(probabilities[sampleX][sampleY], minUpdateProbability, maxUpdateProbability, 0, 1) * 60);
        final int blendedColor = blendColor(borderColor, grayTone, 0.5f);

        return blendedColor;
    }

    /**
     * Blends a color onto a grid element.
     */
    private void blendColorOnGridElement(int x, int y, float targetBrightness, int blendedColor, float influence) {
        gridCells[x][y].image = getClosestGridImage(targetBrightness);
        gridCells[x][y].color = blendColor(gridCells[x][y].color, blendedColor, influence);
    }

    /**
     * Blends two colors based on a blending factor.
     */
    private int blendColor(final int c1, final int c2, final float t) {
        final float r1 = red(c1);
        final float g1 = green(c1);
        final float b1 = blue(c1);

        final float r2 = red(c2);
        final float g2 = green(c2);
        final float b2 = blue(c2);

        final float r = lerp(r1, r2, t);
        final float g = lerp(g1, g2, t);
        final float b = lerp(b1, b2, t);

        return color(r, g, b);
    }

    /**
     * Renders the grid onto the canvas.
     */
    private void renderGrid() {
        noStroke();
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                final GridCell cell = gridCells[x][y];
                if (cell.dirty) {
                    cell.dirty = false;

                    // Draw background
                    fill(0);
                    rect(x * gridSize, y * gridSize, gridSize, gridSize);

                    if (cell.image != null) {
                        tint(cell.color);
                        image(cell.image, x * gridSize, y * gridSize, gridSize, gridSize);
                    }
                }
            }
        }
        // Reset tint
        noTint();
    }

    /**
     * Calculates the average brightness of an image.
     */
    private float calculateAverageBrightness(final PImage img) {
        float totalBrightness = 0;
        img.loadPixels();
        for (final int pixel : img.pixels) {
            totalBrightness += brightness(pixel);
        }
        return totalBrightness / img.pixels.length;
    }

    /**
     * Modulates the brightness of a value with a random offset.
     */
    private float modulateBrightness(final float brightness) {
        if (random(1) > 0.1) {
            return brightness;
        }
        final float randomOffset = random(-20, 20);
        return constrain(brightness + randomOffset, 0, 255);
    }

    /**
     * Retrieves the grid image closest to the target brightness.
     */
    private PImage getClosestGridImage(final float targetBrightness) {
        final Map.Entry<Float, PImage> entry = brightnessToImageMap.ceilingEntry(targetBrightness);
        if (entry != null) {
            return entry.getValue();
        } else {
            return brightnessToImageMap.lastEntry().getValue();
        }
    }

    /**
     * Inner class to store both image and color for each grid cell.
     */
    private static class GridCell {
        int x, y;
        float displacementX, displacementY;
        PImage image;
        int color;
        float lastImageInfluence;
        boolean dirty;

        GridCell() {
            image = null;
            color = 0;
            dirty = false;
            displacementX = 0;
            displacementY = 0;
        }
    }
}

