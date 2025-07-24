import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

class ImageEnhancerApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select an image to enhance");

            if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                try {
                    String imagePath = fileChooser.getSelectedFile().getAbsolutePath();
                    ImageEnhancer enhancer = new ImageEnhancer(imagePath);

                    Object[] options = {"Histogram Equalization", "Fuzzy Enhancement"};
                    int choice = JOptionPane.showOptionDialog(null,
                            "Choose enhancement method:",
                            "Enhancement Method",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]);

                    if (choice == 0) {
                        enhancer.equalizeHistogram();
                    } else {
                        enhancer.applyFuzzyEnhancement();
                    }

                    if (enhancer.getEnhancedImage() != null) {
                        enhancer.displayHistogram();
                        enhancer.saveEnhancedImage("enhanced_" + new File(imagePath).getName());
                        enhancer.showBeforeAfter();

                        JOptionPane.showMessageDialog(null,
                                "Enhancement completed! Check the 'output' folder in your project directory.",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }

                } catch (IOException e) {
                    showError("Error processing image: " + e.getMessage());
                } catch (IllegalStateException e) {
                    showError(e.getMessage());
                }
            }
        });
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}

class ImageEnhancer {
    private BufferedImage originalImage;
    private BufferedImage enhancedImage;
    private int[] histogram;
    private int[] cumulativeHistogram;
    private final String outputDirectory;

    private final double DARK_THRESHOLD = 75;
    private final double GRAY_THRESHOLD_LOW = 75;
    private final double GRAY_THRESHOLD_HIGH = 150;
    private final double BRIGHT_THRESHOLD = 150;

    public ImageEnhancer(String imagePath) throws IOException {
        String projectDir = System.getProperty("user.dir");
        outputDirectory = Paths.get(projectDir, "output").toString();
        new File(outputDirectory).mkdirs();

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) throw new IOException("Image file not found: " + imagePath);

        originalImage = ImageIO.read(imageFile);
        histogram = new int[256];
        cumulativeHistogram = new int[256];
    }

    public BufferedImage getEnhancedImage() {
        return enhancedImage;
    }

    private int getGrayscale(Color color) {
        return (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
    }

    public void calculateHistogram() {
        histogram = new int[256];
        for (int y = 0; y < originalImage.getHeight(); y++) {
            for (int x = 0; x < originalImage.getWidth(); x++) {
                int gray = getGrayscale(new Color(originalImage.getRGB(x, y)));
                histogram[gray]++;
            }
        }
    }

    public void calculateCumulativeHistogram() {
        cumulativeHistogram[0] = histogram[0];
        for (int i = 1; i < 256; i++) {
            cumulativeHistogram[i] = cumulativeHistogram[i - 1] + histogram[i];
        }
    }

    public void equalizeHistogram() {
        enhancedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        calculateHistogram();
        calculateCumulativeHistogram();

        int totalPixels = originalImage.getWidth() * originalImage.getHeight();
        int[] lookupTable = new int[256];
        for (int i = 0; i < 256; i++) {
            lookupTable[i] = (int) (((float) cumulativeHistogram[i] / totalPixels) * 255.0f);
        }

        for (int y = 0; y < originalImage.getHeight(); y++) {
            for (int x = 0; x < originalImage.getWidth(); x++) {
                Color color = new Color(originalImage.getRGB(x, y));
                int gray = getGrayscale(color);
                int newGray = Math.min(255, Math.max(0, lookupTable[gray]));
                enhancedImage.setRGB(x, y, new Color(newGray, newGray, newGray).getRGB());
            }
        }
    }

    public void applyFuzzyEnhancement() {
        enhancedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < originalImage.getHeight(); y++) {
            for (int x = 0; x < originalImage.getWidth(); x++) {
                Color color = new Color(originalImage.getRGB(x, y));
                int intensity = (color.getRed() + color.getGreen() + color.getBlue()) / 3;

                double dark = getDarkMembership(intensity);
                double gray = getGrayMembership(intensity);
                double bright = getBrightMembership(intensity);

                double enhancement = (dark * 1.5 + gray * 1.2 + bright * 0.9) / (dark + gray + bright);
                int newIntensity = Math.min(255, Math.max(0, (int) (intensity * enhancement)));

                enhancedImage.setRGB(x, y, new Color(newIntensity, newIntensity, newIntensity).getRGB());
            }
        }
    }

    private double getDarkMembership(int intensity) {
        if (intensity <= DARK_THRESHOLD) return 1.0;
        if (intensity <= GRAY_THRESHOLD_LOW) return (GRAY_THRESHOLD_LOW - intensity) / (GRAY_THRESHOLD_LOW - DARK_THRESHOLD);
        return 0.0;
    }

    private double getGrayMembership(int intensity) {
        if (intensity <= DARK_THRESHOLD) return 0.0;
        if (intensity <= GRAY_THRESHOLD_LOW) return (intensity - DARK_THRESHOLD) / (GRAY_THRESHOLD_LOW - DARK_THRESHOLD);
        if (intensity <= GRAY_THRESHOLD_HIGH) return 1.0;
        if (intensity <= BRIGHT_THRESHOLD) return (BRIGHT_THRESHOLD - intensity) / (BRIGHT_THRESHOLD - GRAY_THRESHOLD_HIGH);
        return 0.0;
    }

    private double getBrightMembership(int intensity) {
        if (intensity <= GRAY_THRESHOLD_HIGH) return 0.0;
        if (intensity <= BRIGHT_THRESHOLD) return (intensity - GRAY_THRESHOLD_HIGH) / (BRIGHT_THRESHOLD - GRAY_THRESHOLD_HIGH);
        return 1.0;
    }

    public void saveEnhancedImage(String fileName) throws IOException {
        if (enhancedImage == null) throw new IllegalStateException("Run enhancement first.");
        File outputFile = new File(outputDirectory, fileName);
        ImageIO.write(enhancedImage, "png", outputFile);
    }

    public void displayHistogram() {
        calculateHistogram();
        BufferedImage histogramImage = new BufferedImage(512, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = histogramImage.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 512, 400);

        int max = 0;
        for (int i : histogram) if (i > max) max = i;

        g.setColor(Color.BLACK);
        for (int i = 0; i < 256; i++) {
            int height = (int) (((double) histogram[i] / max) * 350);
            g.drawLine(i * 2, 399, i * 2, 399 - height);
            g.drawLine(i * 2 + 1, 399, i * 2 + 1, 399 - height);
        }
        g.dispose();

        try {
            File output = new File(outputDirectory, "histogram.png");
            ImageIO.write(histogramImage, "png", output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showBeforeAfter() {
        JFrame frame = new JFrame("Before and After Enhancement");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(2 * originalImage.getWidth(), originalImage.getHeight() + 30);

        JPanel panel = new JPanel(new GridLayout(1, 2));
        panel.add(new JLabel(new ImageIcon(originalImage)));
        panel.add(new JLabel(new ImageIcon(enhancedImage)));

        frame.add(panel);
        frame.setVisible(true);
    }
}
