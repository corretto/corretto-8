import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/*
 * @summary This is a helper class to find the location of a system tray icon,
 *          and skip some OS specific cases in tests.
 * @library ../../../../../lib/testlibrary
 * @build ExtendedRobot SystemTrayIconHelper
 */
public class SystemTrayIconHelper {

    static Frame frame;

    /**
     * Call this method if the tray icon need to be followed in an automated manner
     * This method will be called by automated testcases
     */
    static Point getTrayIconLocation(TrayIcon icon) throws Exception {
        if (icon == null) {
            return null;
        }

        //This is added just in case the tray's native menu is visible.
        //It has to be hidden if visible. For that, we are showing a Frame
        //and clicking on it - the assumption is, the menu will
        //be closed if another window is clicked
        ExtendedRobot robot = new ExtendedRobot();
        try {
           EventQueue.invokeAndWait(() -> {
               frame = new Frame();
               frame.setSize(100, 100);
               frame.setVisible(true);
           });
            robot.mouseMove(frame.getLocationOnScreen().x + frame.getWidth() / 2,
                    frame.getLocationOnScreen().y + frame.getHeight() / 2);
            robot.waitForIdle();
            robot.click();
            EventQueue.invokeAndWait(frame::dispose);
        } catch (Exception e) {
            return null;
        }

        if (System.getProperty("os.name").startsWith("Win")) {
            try {
                // sun.awt.windows.WTrayIconPeer
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Dimension iconSize = icon.getSize();

                int width = (int) iconSize.getWidth();
                int height = (int) iconSize.getHeight();

                // Some previously created icons may not be removed
                // from tray until mouse move on it. So we glide
                // through the whole tray bar.
                robot.glide((int) screenSize.getWidth(), (int) (screenSize.getHeight()-15), 0, (int) (screenSize.getHeight() - 15), 1, 2);

                BufferedImage screen = robot.createScreenCapture(new Rectangle(screenSize));

                for (int x = (int) (screenSize.getWidth()-width); x > 0; x--) {
                    for (int y = (int) (screenSize.getHeight()-height); y > (screenSize.getHeight()-50); y--) {
                        if (imagesEquals(((BufferedImage)icon.getImage()).getSubimage(0, 0, width, height), screen.getSubimage(x, y, width, height))) {
                            return new Point(x+5, y+5);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else if (System.getProperty("os.name").startsWith("Mac")) {
            Point2D point2d;
            try {
                // sun.lwawt.macosx.CTrayIcon
                Field f_peer = getField( java.awt.TrayIcon.class, "peer");

                Object peer = f_peer.get(icon);
                Method m_getModel = peer.getClass().getDeclaredMethod(
                        "getModel");
                m_getModel.setAccessible(true);
                long model = (Long) (m_getModel.invoke(peer, new Object[]{}));
                Method m_getLocation = peer.getClass().getDeclaredMethod(
                        "nativeGetIconLocation", new Class[]{Long.TYPE});
                m_getLocation.setAccessible(true);
                point2d = (Point2D)m_getLocation.invoke(peer, new Object[]{model});
                Point po = new Point((int)(point2d.getX()), (int)(point2d.getY()));
                po.translate(10, -5);
                return po;
            }catch(Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            try {
                // sun.awt.X11.XTrayIconPeer
                Field f_peer = getField(java.awt.TrayIcon.class, "peer");

                Object peer = f_peer.get(icon);
                Method m_getLOS = peer.getClass().getDeclaredMethod(
                        "getLocationOnScreen", new Class[]{});
                m_getLOS.setAccessible(true);
                Point point = (Point)m_getLOS.invoke(peer, new Object[]{});
                point.translate(5, 5);
                return point;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    static Field getField(final Class clz, final String fieldName) {
        Field res = null;
        try {
            res = (Field)AccessController.doPrivileged((PrivilegedExceptionAction) () -> {
                Field f = clz.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            });
        } catch (PrivilegedActionException ex) {
            ex.printStackTrace();
        }
        return res;
    }

    static boolean imagesEquals(BufferedImage img1, BufferedImage img2) {
        for (int x = 0; x < img1.getWidth(); x++) {
            for (int y = 0; y < img1.getHeight(); y++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y))
                    return false;
            }
        }
        return true;
    }

    static void doubleClick(Robot robot) {
        if (System.getProperty("os.name").startsWith("Mac")) {
            robot.mousePress(InputEvent.BUTTON3_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON3_MASK);
        } else {
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        }
    }

    // Method for skipping some OS specific cases
    static boolean skip(int button) {
        if (System.getProperty("os.name").toLowerCase().startsWith("win")){
            if (button == InputEvent.BUTTON1_MASK){
                // See JDK-6827035
                return true;
            }
        } else if (System.getProperty("os.name").toLowerCase().contains("os x")){
            // See JDK-7153700
            return true;
        }
        return false;
    }
}
