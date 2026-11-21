import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

// Rasterises Lucide SVG icons into white-on-transparent PNGs the client tints at draw time.
// java -Djava.awt.headless=true tools/LucideRaster.java <lucide icons dir> <output dir> <size px> <icon> [icon...]
public final class LucideRaster {
    private static final Pattern ELEMENT = Pattern.compile("<(path|rect|circle|ellipse|line|polyline|polygon)\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)=\"([^\"]*)\"");

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        int size = Integer.parseInt(args[2]);
        Files.createDirectories(out);

        for (int i = 3; i < args.length; i++) {
            String name = args[i];
            String svg = Files.readString(in.resolve(name + ".svg"));
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(size / 24.0, size / 24.0);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Matcher matcher = ELEMENT.matcher(svg);

            while (matcher.find()) {
                Map<String, String> attrs = attributes(matcher.group(2));
                Shape shape = shape(matcher.group(1), attrs);
                if (shape != null) {
                    String fill = attrs.get("fill");
                    if (fill != null && !fill.equals("none")) {
                        g.fill(shape);
                    }

                    g.draw(shape);
                }
            }

            g.dispose();
            ImageIO.write(image, "png", out.resolve(name + ".png").toFile());
            // linear filtering so the icon stays smooth when the GUI scales it down
            Files.writeString(out.resolve(name + ".png.mcmeta"), "{\"texture\":{\"blur\":true}}\n");
            System.out.println("wrote " + name + ".png");
        }
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = ATTR.matcher(text);

        while (matcher.find()) {
            map.put(matcher.group(1), matcher.group(2));
        }

        return map;
    }

    private static double num(Map<String, String> attrs, String key) {
        String value = attrs.get(key);
        return value == null ? 0.0 : Double.parseDouble(value);
    }

    private static Shape shape(String tag, Map<String, String> a) {
        return switch (tag) {
            case "path" -> parsePath(a.get("d"));
            case "rect" -> {
                double rx = a.containsKey("rx") ? num(a, "rx") : num(a, "ry");
                double ry = a.containsKey("ry") ? num(a, "ry") : rx;
                yield new RoundRectangle2D.Double(num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height"), rx * 2.0, ry * 2.0);
            }
            case "circle" -> {
                double r = num(a, "r");
                yield new Ellipse2D.Double(num(a, "cx") - r, num(a, "cy") - r, r * 2.0, r * 2.0);
            }
            case "ellipse" -> new Ellipse2D.Double(num(a, "cx") - num(a, "rx"), num(a, "cy") - num(a, "ry"), num(a, "rx") * 2.0, num(a, "ry") * 2.0);
            case "line" -> new Line2D.Double(num(a, "x1"), num(a, "y1"), num(a, "x2"), num(a, "y2"));
            case "polyline", "polygon" -> {
                Scanner sc = new Scanner(a.get("points"));
                Path2D.Double p = new Path2D.Double();
                p.moveTo(sc.number(), sc.number());

                while (sc.hasNumber()) {
                    p.lineTo(sc.number(), sc.number());
                }

                if (tag.equals("polygon")) {
                    p.closePath();
                }

                yield p;
            }
            default -> null;
        };
    }

    private static Path2D parsePath(String d) {
        Path2D.Double p = new Path2D.Double();
        Scanner sc = new Scanner(d);
        double x = 0.0, y = 0.0, startX = 0.0, startY = 0.0, ctrlX = 0.0, ctrlY = 0.0;
        char last = ' ';

        while (sc.skip()) {
            char cmd = sc.command();
            boolean rel = Character.isLowerCase(cmd);
            char op = Character.toUpperCase(cmd);
            boolean first = true;

            do {
                double ox = rel ? x : 0.0;
                double oy = rel ? y : 0.0;
                switch (op) {
                    case 'M' -> {
                        x = ox + sc.number();
                        y = oy + sc.number();
                        if (first) {
                            p.moveTo(x, y);
                            startX = x;
                            startY = y;
                        } else {
                            p.lineTo(x, y);
                        }
                    }
                    case 'L' -> {
                        x = ox + sc.number();
                        y = oy + sc.number();
                        p.lineTo(x, y);
                    }
                    case 'H' -> {
                        x = ox + sc.number();
                        p.lineTo(x, y);
                    }
                    case 'V' -> {
                        y = oy + sc.number();
                        p.lineTo(x, y);
                    }
                    case 'C' -> {
                        double x1 = ox + sc.number(), y1 = oy + sc.number();
                        ctrlX = ox + sc.number();
                        ctrlY = oy + sc.number();
                        x = ox + sc.number();
                        y = oy + sc.number();
                        p.curveTo(x1, y1, ctrlX, ctrlY, x, y);
                    }
                    case 'S' -> {
                        boolean smooth = last == 'C' || last == 'S';
                        double x1 = smooth ? 2.0 * x - ctrlX : x, y1 = smooth ? 2.0 * y - ctrlY : y;
                        ctrlX = ox + sc.number();
                        ctrlY = oy + sc.number();
                        x = ox + sc.number();
                        y = oy + sc.number();
                        p.curveTo(x1, y1, ctrlX, ctrlY, x, y);
                    }
                    case 'Q' -> {
                        ctrlX = ox + sc.number();
                        ctrlY = oy + sc.number();
                        x = ox + sc.number();
                        y = oy + sc.number();
                        p.quadTo(ctrlX, ctrlY, x, y);
                    }
                    case 'T' -> {
                        boolean smooth = last == 'Q' || last == 'T';
                        ctrlX = smooth ? 2.0 * x - ctrlX : x;
                        ctrlY = smooth ? 2.0 * y - ctrlY : y;
                        x = ox + sc.number();
                        y = oy + sc.number();
                        p.quadTo(ctrlX, ctrlY, x, y);
                    }
                    case 'A' -> {
                        double rx = sc.number(), ry = sc.number(), rot = sc.number();
                        boolean large = sc.flag(), sweep = sc.flag();
                        double nx = ox + sc.number(), ny = oy + sc.number();
                        arc(p, x, y, rx, ry, rot, large, sweep, nx, ny);
                        x = nx;
                        y = ny;
                    }
                    case 'Z' -> {
                        p.closePath();
                        x = startX;
                        y = startY;
                    }
                    default -> throw new IllegalArgumentException("unsupported path command " + cmd);
                }

                last = op;
                first = false;
            } while (op != 'Z' && sc.hasNumber());
        }

        return p;
    }

    // SVG elliptical arc, converted to cubic Bézier segments (W3C SVG implementation notes, F.6)
    private static void arc(Path2D p, double x1, double y1, double rx, double ry, double rotDeg, boolean large, boolean sweep, double x2, double y2) {
        if (x1 == x2 && y1 == y2) {
            return;
        }

        if (rx == 0.0 || ry == 0.0) {
            p.lineTo(x2, y2);
            return;
        }

        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(rotDeg), cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x1 - x2) / 2.0, dy = (y1 - y2) / 2.0;
        double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1.0) {
            double s = Math.sqrt(lambda);
            rx *= s;
            ry *= s;
        }

        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coef = (large == sweep ? -1.0 : 1.0) * Math.sqrt(Math.max(0.0, num / den));
        double cxp = coef * (rx * y1p / ry), cyp = coef * (-ry * x1p / rx);
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0, cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0;
        double th1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double dth = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && dth > 0.0) {
            dth -= Math.PI * 2.0;
        } else if (sweep && dth < 0.0) {
            dth += Math.PI * 2.0;
        }

        int segments = (int)Math.ceil(Math.abs(dth) / (Math.PI / 2.0));
        double step = dth / segments, t = 4.0 / 3.0 * Math.tan(step / 4.0);

        for (int k = 0; k < segments; k++) {
            double a1 = th1 + k * step, a2 = a1 + step;
            double[] c1 = map(Math.cos(a1) - t * Math.sin(a1), Math.sin(a1) + t * Math.cos(a1), cx, cy, rx, ry, cos, sin);
            double[] c2 = map(Math.cos(a2) + t * Math.sin(a2), Math.sin(a2) - t * Math.cos(a2), cx, cy, rx, ry, cos, sin);
            double[] end = map(Math.cos(a2), Math.sin(a2), cx, cy, rx, ry, cos, sin);
            p.curveTo(c1[0], c1[1], c2[0], c2[1], end[0], end[1]);
        }
    }

    private static double[] map(double ux, double uy, double cx, double cy, double rx, double ry, double cos, double sin) {
        return new double[]{cx + rx * ux * cos - ry * uy * sin, cy + rx * ux * sin + ry * uy * cos};
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double a = Math.acos(Math.max(-1.0, Math.min(1.0, (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy)))));
        return ux * vy - uy * vx < 0.0 ? -a : a;
    }

    private static final class Scanner {
        private final String s;
        private int i;

        Scanner(String s) {
            this.s = s;
        }

        boolean skip() {
            while (this.i < this.s.length() && (Character.isWhitespace(this.s.charAt(this.i)) || this.s.charAt(this.i) == ',')) {
                this.i++;
            }

            return this.i < this.s.length();
        }

        char command() {
            return this.s.charAt(this.i++);
        }

        boolean hasNumber() {
            if (!this.skip()) {
                return false;
            }

            char c = this.s.charAt(this.i);
            return Character.isDigit(c) || c == '-' || c == '+' || c == '.';
        }

        double number() {
            this.skip();
            int start = this.i;
            if (this.s.charAt(this.i) == '-' || this.s.charAt(this.i) == '+') {
                this.i++;
            }

            boolean dot = false;

            while (this.i < this.s.length()) {
                char c = this.s.charAt(this.i);
                if (Character.isDigit(c)) {
                    this.i++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    this.i++;
                } else {
                    break;
                }
            }

            if (this.i < this.s.length() && (this.s.charAt(this.i) == 'e' || this.s.charAt(this.i) == 'E')) {
                this.i++;
                if (this.s.charAt(this.i) == '-' || this.s.charAt(this.i) == '+') {
                    this.i++;
                }

                while (this.i < this.s.length() && Character.isDigit(this.s.charAt(this.i))) {
                    this.i++;
                }
            }

            return Double.parseDouble(this.s.substring(start, this.i));
        }

        // arc flags are single characters and may be packed with no separator ("013")
        boolean flag() {
            this.skip();
            return this.s.charAt(this.i++) == '1';
        }
    }
}
