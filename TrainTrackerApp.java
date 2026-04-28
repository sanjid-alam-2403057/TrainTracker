import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;

// ─────────────────────────────────────────────
//  Abstract base class
// ─────────────────────────────────────────────
abstract class Transport {
    abstract String getCategory();
}

// ─────────────────────────────────────────────
// Persistable interface
// ─────────────────────────────────────────────
interface Persistable {
    void saveToFile();
}

// ─────────────────────────────────────────────
// Station – holds name + scheduled departure
// ─────────────────────────────────────────────
class Station {
    String name;
    String scheduledDep; // "HH:mm"
    boolean reached;

    Station(String name, String scheduledDep) {
        this.name = name;
        this.scheduledDep = scheduledDep;
        this.reached = false;
    }

    /** Returns minutes-since-midnight for scheduledDep */
    int depMinutes() {
        String[] parts = scheduledDep.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}

// ─────────────────────────────────────────────
// Train
// ─────────────────────────────────────────────
class Train extends Transport implements Persistable {

    String number, name, start, destination, offDay;
    int totalDistance, avgSpeedKmh;
    List<Station> stops;

    Train(String number, String name, String start, String destination,
            String offDay, int totalDistance, int avgSpeedKmh, List<Station> stops) {
        this.number = number;
        this.name = name;
        this.start = start;
        this.destination = destination;
        this.offDay = offDay;
        this.totalDistance = totalDistance;
        this.avgSpeedKmh = avgSpeedKmh;
        this.stops = stops;
    }

    // ── Helpers ──────────────────────────────

    /** Departure time of first stop */
    String departure() {
        return stops.get(0).scheduledDep;
    }

    /** Scheduled arrival = departure time of last stop */
    String scheduledArrival() {
        return stops.get(stops.size() - 1).scheduledDep;
    }

    int totalStops() {
        return stops.size();
    }

    /**
     * Given current time in minutes-since-midnight,
     * compute how many minutes have elapsed since departure,
     * accounting for overnight trains.
     */
    int elapsedMinutes(int nowMin) {
        int depMin = stops.get(0).depMinutes();
        int elapsed = nowMin - depMin;
        if (elapsed < 0)
            elapsed += 1440; // overnight crossing
        return elapsed;
    }

    /** Total journey duration in minutes */
    int journeyDuration() {
        int dep = stops.get(0).depMinutes();
        int arr = stops.get(stops.size() - 1).depMinutes();
        int dur = arr - dep;
        if (dur < 0)
            dur += 1440; // overnight
        return dur;
    }

    /** Progress 0.0 – 1.0 */
    double progress(int nowMin) {
        int elapsed = elapsedMinutes(nowMin);
        int dur = journeyDuration();
        if (dur == 0)
            return 0;
        return Math.min(1.0, (double) elapsed / dur);
    }

    /** km covered so far */
    int coveredKm(int nowMin) {
        return (int) Math.round(progress(nowMin) * totalDistance);
    }

    /** km remaining */
    int remainingKm(int nowMin) {
        return totalDistance - coveredKm(nowMin);
    }

    /**
     * Returns the index of the stop the train is currently AT or has most recently
     * passed.
     * Returns -1 if journey hasn't started yet.
     */
    int currentStopIndex(int nowMin) {
        int depBase = stops.get(0).depMinutes();

        for (int i = stops.size() - 1; i >= 0; i--) {
            int stopMin = stops.get(i).depMinutes();
            // normalise overnight
            int stopElapsed = stopMin - depBase;
            if (stopElapsed < 0)
                stopElapsed += 1440;

            if (elapsedMinutes(nowMin) >= stopElapsed)
                return i;
        }
        return -1;
    }

    /** Next station name; "Arrived" if journey complete */
    String nextStationName(int nowMin) {
        int ci = currentStopIndex(nowMin);
        if (ci < 0)
            return stops.get(0).name;
        if (ci >= stops.size() - 1)
            return "Arrived at " + destination;
        return stops.get(ci + 1).name;
    }

    /** Scheduled departure of next station; "" if arrived */
    String nextStationETA(int nowMin) {
        int ci = currentStopIndex(nowMin);
        if (ci < 0)
            return stops.get(0).scheduledDep;
        if (ci >= stops.size() - 1)
            return "—";
        return stops.get(ci + 1).scheduledDep;
    }

    /** Live ETA at final destination (HH:mm) based on remaining km + avg speed */
    String liveArrivalETA(int nowMin) {
        int remKm = remainingKm(nowMin);
        if (remKm <= 0)
            return scheduledArrival();
        double hoursLeft = (double) remKm / avgSpeedKmh;
        int etaMin = (nowMin + (int) Math.round(hoursLeft * 60)) % 1440;
        return String.format("%02d:%02d", etaMin / 60, etaMin % 60);
    }

    /** Remaining time formatted as H h MM m */
    String remainingTimeStr(int nowMin) {
        int remKm = remainingKm(nowMin);
        if (remKm <= 0)
            return "0 h 00 m";
        double hoursLeft = (double) remKm / avgSpeedKmh;
        int h = (int) hoursLeft;
        int m = (int) Math.round((hoursLeft - h) * 60);
        return h + " h " + String.format("%02d", m) + " m";
    }

    @Override
    public String getCategory() {
        return "Railway";
    }

    @Override
    public void saveToFile() {
        try (BufferedWriter w = new BufferedWriter(new FileWriter("train_data.txt", true))) {
            w.write(number + "," + name + "," + start + "," + LocalDateTime.now() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return number + " – " + name;
    }
}

// ─────────────────────────────────────────────
// Custom progress bar panel
// ─────────────────────────────────────────────
class RouteProgressPanel extends JPanel {

    private Train train;
    private int nowMin;

    RouteProgressPanel() {
        setBackground(new Color(30, 30, 30));
        setPreferredSize(new Dimension(420, 80));
    }

    void update(Train t, int nowMin) {
        this.train = t;
        this.nowMin = nowMin;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (train == null)
            return;

        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int trackX = 20, trackY = 30, trackW = getWidth() - 40, trackH = 6;
        int stops = train.totalStops();
        double pct = train.progress(nowMin);

        // Background track
        g.setColor(new Color(80, 80, 80));
        g.fillRoundRect(trackX, trackY, trackW, trackH, trackH, trackH);

        // Filled portion
        g.setColor(new Color(200, 0, 0));
        g.fillRoundRect(trackX, trackY, (int) (trackW * pct), trackH, trackH, trackH);

        // Stop dots
        int ci = train.currentStopIndex(nowMin);
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));

        for (int i = 0; i < stops; i++) {
            double pos = (stops == 1) ? 0 : (double) i / (stops - 1);
            int cx = trackX + (int) (pos * trackW);
            int cy = trackY + trackH / 2;
            int r = 7;

            if (i < ci)
                g.setColor(new Color(180, 0, 0));
            else if (i == ci)
                g.setColor(new Color(255, 215, 0));
            else
                g.setColor(new Color(120, 120, 120));

            g.fillOval(cx - r, cy - r, r * 2, r * 2);

            // Station label below dot
            g.setColor(new Color(200, 200, 200));
            String label = train.stops.get(i).name;
            if (label.length() > 8)
                label = label.substring(0, 7) + "…";
            FontMetrics fm = g.getFontMetrics();
            int lx = cx - fm.stringWidth(label) / 2;
            g.drawString(label, lx, trackY + trackH + 18);
        }

        // Train icon (small triangle) at current progress
        int tx = trackX + (int) (trackW * pct);
        int ty = trackY - 10;
        g.setColor(new Color(255, 215, 0));
        int[] px = { tx - 6, tx + 6, tx };
        int[] py = { ty, ty, ty + 8 };
        g.fillPolygon(px, py, 3);
    }
}

// ─────────────────────────────────────────────
// Main Application
// ─────────────────────────────────────────────
public class TrainTrackerApp extends JFrame {

    // ── UI components ─────────────────────────
    private JComboBox<Train> trainCombo;
    private JLabel lblNumber, lblName, lblCategory, lblOffDay, lblTotalDist, lblTotalStops;
    private JLabel lblStart, lblDeparture, lblDestination;
    private JLabel lblScheduledArr, lblLiveETA, lblNextStation, lblNextETA;
    private JLabel lblCovered, lblRemaining, lblRemTime, lblSpeed;
    private JLabel lblClock, lblStatus;
    private RouteProgressPanel routePanel;
    private JPanel offDayBanner;
    private JLabel offDayMsg;
    private javax.swing.Timer liveTimer;

    // ── Train data ────────────────────────────
    private List<Train> trains;

    // ── Colours ───────────────────────────────
    private static final Color DARK_RED = new Color(139, 0, 0);
    private static final Color DARK_BG = new Color(28, 28, 36);
    private static final Color CARD_BG = new Color(38, 38, 50);
    private static final Color ACCENT = new Color(255, 215, 0);
    private static final Color TEXT_MAIN = new Color(230, 230, 230);
    private static final Color TEXT_MUTED = new Color(140, 140, 160);
    private static final Color TEXT_RED = new Color(255, 90, 90);

    public TrainTrackerApp() {
        super("Bangladesh Train Tracker");
        buildTrainData();
        buildUI();
        startLiveClock();
        trainCombo.setSelectedIndex(0);
    }

    // ─────────────────────────────────────────
    // Build train data with full stop schedules
    // ─────────────────────────────────────────
    private void buildTrainData() {
        trains = new ArrayList<>();

        // 754 Silkcity Express – Rajshahi → Dhaka
        List<Station> silkStops = Arrays.asList(
                new Station("Rajshahi", "07:40"),
                new Station("Natore", "08:15"),
                new Station("Baraigram", "08:45"),
                new Station("Ullapara", "09:10"),
                new Station("Sirajganj", "09:40"),
                new Station("Jamtoil", "10:05"),
                new Station("Solop", "10:35"),
                new Station("Jamtal", "11:00"),
                new Station("Joydevpur", "12:00"),
                new Station("Tongi", "12:20"),
                new Station("Airport", "12:40"),
                new Station("Dhaka", "13:20"));
        trains.add(new Train("754", "Silkcity Express", "Rajshahi", "Dhaka",
                "Sunday", 255, 52, silkStops));

        // 759 Padma Express – Dhaka → Rajshahi (overnight)
        List<Station> padmaStops = Arrays.asList(
                new Station("Dhaka", "23:00"),
                new Station("Airport", "23:25"),
                new Station("Tongi", "23:45"),
                new Station("Joydevpur", "00:10"),
                new Station("Jamtal", "00:55"),
                new Station("Abdulpur", "01:20"),
                new Station("Ishwardi", "02:10"),
                new Station("Natore", "02:50"),
                new Station("Rajshahi", "04:30"));
        trains.add(new Train("759", "Padma Express", "Dhaka", "Rajshahi",
                "Tuesday", 255, 55, padmaStops));

        // 773 Kalni Express – Sylhet → Dhaka
        List<Station> kalniStops = Arrays.asList(
                new Station("Sylhet", "06:15"),
                new Station("Srimangal", "07:30"),
                new Station("Shamshernagar", "07:55"),
                new Station("Bhairab", "09:00"),
                new Station("Narsingdi", "09:30"),
                new Station("Tongi", "10:10"),
                new Station("Airport", "10:30"),
                new Station("Tejgaon", "10:45"),
                new Station("Kamlapur", "11:00"),
                new Station("Dhaka", "11:15"));
        trains.add(new Train("773", "Kalni Express", "Sylhet", "Dhaka",
                "Friday", 310, 48, kalniStops));

        // 705 Subarna Express – Dhaka → Chattogram
        List<Station> subarnaStops = Arrays.asList(
                new Station("Dhaka", "07:00"),
                new Station("Narsingdi", "07:55"),
                new Station("Brahmanbaria", "08:40"),
                new Station("Akhaura", "09:05"),
                new Station("Comilla", "09:45"),
                new Station("Laksham", "10:20"),
                new Station("Feni", "11:00"),
                new Station("Chittagong", "12:30"));
        trains.add(new Train("705", "Subarna Express", "Dhaka", "Chittagong",
                "Wednesday", 320, 58, subarnaStops));
    }

    // ─────────────────────────────────────────
    // Build UI
    // ─────────────────────────────────────────
    private void buildUI() {
        setSize(820, 620);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(DARK_BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ── Header ────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DARK_RED);
        p.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        JLabel title = new JLabel("🚆  Bangladesh Train Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Monospaced", Font.BOLD, 18));
        p.add(title, BorderLayout.WEST);

        lblClock = new JLabel("--:--:--");
        lblClock.setForeground(new Color(255, 220, 220));
        lblClock.setFont(new Font("Monospaced", Font.PLAIN, 13));
        p.add(lblClock, BorderLayout.EAST);
        return p;
    }

    // ── Centre ────────────────────────────────
    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBackground(DARK_BG);
        center.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));

        // Top: selector + off-day banner
        JPanel top = new JPanel(new BorderLayout(8, 6));
        top.setBackground(DARK_BG);

        JPanel selectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selectorRow.setBackground(DARK_BG);
        JLabel sel = label("Select Train:", TEXT_MUTED, 13, Font.PLAIN);
        trainCombo = new JComboBox<>(trains.toArray(new Train[0]));
        trainCombo.setBackground(CARD_BG);
        trainCombo.setForeground(TEXT_MAIN);
        trainCombo.setFont(new Font("Monospaced", Font.PLAIN, 13));
        trainCombo.setPreferredSize(new Dimension(240, 30));
        trainCombo.addActionListener(e -> refresh());
        selectorRow.add(sel);
        selectorRow.add(trainCombo);

        offDayBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        offDayBanner.setBackground(new Color(100, 20, 20));
        offDayBanner.setBorder(BorderFactory.createLineBorder(new Color(180, 60, 60)));
        offDayMsg = label("", new Color(255, 180, 180), 12, Font.PLAIN);
        offDayBanner.add(offDayMsg);
        offDayBanner.setVisible(false);

        top.add(selectorRow, BorderLayout.NORTH);
        top.add(offDayBanner, BorderLayout.CENTER);
        center.add(top, BorderLayout.NORTH);

        // Cards row
        JPanel cards = new JPanel(new GridLayout(1, 2, 10, 0));
        cards.setBackground(DARK_BG);
        cards.add(buildDetailsCard());
        cards.add(buildLiveCard());
        center.add(cards, BorderLayout.CENTER);

        // Bottom: route progress + stats
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBackground(DARK_BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel routeCard = card("Route Progress");
        routePanel = new RouteProgressPanel();
        routeCard.add(routePanel, BorderLayout.CENTER);
        bottom.add(routeCard, BorderLayout.CENTER);
        bottom.add(buildStatsRow(), BorderLayout.SOUTH);

        center.add(bottom, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildDetailsCard() {
        JPanel card = card("Train Details");
        lblNumber = infoLabel();
        lblName = infoLabel();
        lblCategory = infoLabel();
        lblOffDay = infoLabel();
        lblTotalDist = infoLabel();
        lblTotalStops = infoLabel();

        addRow(card, "Train No.", lblNumber);
        addRow(card, "Name", lblName);
        addRow(card, "Category", lblCategory);
        addRow(card, "Off Day", lblOffDay);
        addRow(card, "Total Distance", lblTotalDist);
        addRow(card, "Total Stops", lblTotalStops);
        return card;
    }

    private JPanel buildLiveCard() {
        JPanel card = card("Live Status");
        lblStart = infoLabel();
        lblDeparture = infoLabel();
        lblDestination = infoLabel();
        lblScheduledArr = infoLabel();
        lblLiveETA = infoLabel();
        lblNextStation = infoLabel();
        lblNextETA = infoLabel();

        lblLiveETA.setForeground(ACCENT);
        lblNextStation.setForeground(ACCENT);

        addRow(card, "Origin", lblStart);
        addRow(card, "Departure", lblDeparture);
        addRow(card, "Destination", lblDestination);
        addRow(card, "Scheduled Arr.", lblScheduledArr);
        addRow(card, "Live ETA", lblLiveETA);
        addRow(card, "Next Station", lblNextStation);
        addRow(card, "Next Station ETA", lblNextETA);
        return card;
    }

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
        row.setBackground(DARK_BG);

        lblCovered = statLabel();
        lblRemaining = statLabel();
        lblRemTime = statLabel();
        lblSpeed = statLabel();

        row.add(statCard("Covered", lblCovered, "km"));
        row.add(statCard("Remaining", lblRemaining, "km"));
        row.add(statCard("Time Left", lblRemTime, ""));
        row.add(statCard("Avg Speed", lblSpeed, "km/h"));
        return row;
    }

    // ── Footer ────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(20, 20, 28));
        p.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));

        lblStatus = label("", TEXT_MUTED, 12, Font.PLAIN);
        p.add(lblStatus, BorderLayout.WEST);

        JLabel copy = label("Copyright © 2026, CSE RUET", TEXT_MUTED, 11, Font.PLAIN);
        p.add(copy, BorderLayout.EAST);
        return p;
    }

    // ─────────────────────────────────────────
    // Refresh – called every second
    // ─────────────────────────────────────────
    private void refresh() {
        Train t = (Train) trainCombo.getSelectedItem();
        if (t == null)
            return;

        LocalTime now = LocalTime.now();
        int nowMin = now.getHour() * 60 + now.getMinute() + now.getSecond() / 60;
        String today = LocalDate.now().getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        // Off-day check
        boolean isOffDay = today.equalsIgnoreCase(t.offDay);
        offDayBanner.setVisible(isOffDay);
        if (isOffDay)
            offDayMsg.setText("⚠  " + t.name + " does not operate on " + t.offDay + "s.");

        // Details card
        lblNumber.setText(t.number);
        lblName.setText(t.name);
        lblCategory.setText(t.getCategory());
        lblOffDay.setText(t.offDay);
        lblTotalDist.setText(t.totalDistance + " km");
        lblTotalStops.setText(String.valueOf(t.totalStops()));

        // Live status card
        lblStart.setText(t.start);
        lblDeparture.setText(t.departure());
        lblDestination.setText(t.destination);
        lblScheduledArr.setText(t.scheduledArrival());

        double prog = t.progress(nowMin);
        if (prog <= 0.0) {
            lblLiveETA.setText("Not departed yet");
            lblNextStation.setText(t.stops.get(0).name);
            lblNextETA.setText(t.departure());
        } else if (prog >= 1.0) {
            lblLiveETA.setText("Arrived ✔");
            lblNextStation.setText("Journey complete");
            lblNextETA.setText("—");
        } else {
            lblLiveETA.setText(t.liveArrivalETA(nowMin));
            lblNextStation.setText(t.nextStationName(nowMin));
            lblNextETA.setText(t.nextStationETA(nowMin));
        }

        // Stats row
        int cov = t.coveredKm(nowMin);
        int rem = t.remainingKm(nowMin);
        lblCovered.setText(cov + " km");
        lblRemaining.setText(rem + " km");
        lblRemTime.setText(prog >= 1.0 ? "Arrived" : t.remainingTimeStr(nowMin));
        lblSpeed.setText(t.avgSpeedKmh + " km/h");

        // Route progress panel
        routePanel.update(t, nowMin);

        // Status bar
        int pct = (int) Math.round(prog * 100);
        lblStatus.setText("Progress: " + pct + "%  |  " +
                cov + " km covered  |  " + rem + " km remaining");

        t.saveToFile();
    }

    // ─────────────────────────────────────────
    // Live clock timer (every second)
    // ─────────────────────────────────────────
    private void startLiveClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        liveTimer = new javax.swing.Timer(1000, e -> {
            LocalDateTime now = LocalDateTime.now();
            String day = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            lblClock.setText(now.format(fmt) + "  (" + day + ")");
            refresh();
        });
        liveTimer.setInitialDelay(0);
        liveTimer.start();
    }

    // ─────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────
    private JPanel card(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 80)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel t = new JLabel(title);
        t.setForeground(TEXT_MUTED);
        t.setFont(new Font("Monospaced", Font.PLAIN, 11));
        t.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        p.add(t, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setBackground(CARD_BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        p.add(body, BorderLayout.CENTER);
        // Return body so addRow targets it
        p.putClientProperty("body", body);
        return p;
    }

    private void addRow(JPanel card, String labelText, JLabel valueLabel) {
        JPanel body = (JPanel) card.getClientProperty("body");
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(CARD_BG);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 70)));

        JLabel lbl = label(labelText, TEXT_MUTED, 12, Font.PLAIN);
        lbl.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 8));
        row.add(lbl, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        body.add(row);
    }

    private JPanel statCard(String title, JLabel valueLabel, String unit) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 80)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel t = label(title, TEXT_MUTED, 11, Font.PLAIN);
        p.add(t, BorderLayout.NORTH);
        p.add(valueLabel, BorderLayout.CENTER);
        if (!unit.isEmpty()) {
            p.add(label(unit, TEXT_MUTED, 11, Font.PLAIN), BorderLayout.SOUTH);
        }
        return p;
    }

    private JLabel infoLabel() {
        JLabel l = new JLabel("—");
        l.setForeground(TEXT_MAIN);
        l.setFont(new Font("Monospaced", Font.PLAIN, 13));
        l.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        return l;
    }

    private JLabel statLabel() {
        JLabel l = new JLabel("—");
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 16));
        return l;
    }

    private static JLabel label(String text, Color color, int size, int style) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(new Font("Monospaced", style, size));
        return l;
    }

    // ─────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TrainTrackerApp app = new TrainTrackerApp();
            app.setLocationRelativeTo(null);
            app.setVisible(true);
        });
    }
}
