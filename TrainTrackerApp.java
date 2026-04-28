import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
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
// Station
// ─────────────────────────────────────────────
class Station {
    String name;
    String scheduledDep;
    boolean reached;

    Station(String name, String scheduledDep) {
        this.name = name;
        this.scheduledDep = scheduledDep;
        this.reached = false;
    }

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

    String departure() {
        return stops.get(0).scheduledDep;
    }

    String scheduledArrival() {
        return stops.get(stops.size() - 1).scheduledDep;
    }

    int totalStops() {
        return stops.size();
    }

    int elapsedMinutes(int nowMin) {
        int elapsed = nowMin - stops.get(0).depMinutes();
        if (elapsed < 0)
            elapsed += 1440;
        return elapsed;
    }

    int journeyDuration() {
        int dur = stops.get(stops.size() - 1).depMinutes() - stops.get(0).depMinutes();
        if (dur < 0)
            dur += 1440;
        return dur;
    }

    double progress(int nowMin) {
        int dur = journeyDuration();
        if (dur == 0)
            return 0;
        return Math.min(1.0, (double) elapsedMinutes(nowMin) / dur);
    }

    int coveredKm(int nowMin) {
        return (int) Math.round(progress(nowMin) * totalDistance);
    }

    int remainingKm(int nowMin) {
        return totalDistance - coveredKm(nowMin);
    }

    int currentStopIndex(int nowMin) {
        int depBase = stops.get(0).depMinutes();
        for (int i = stops.size() - 1; i >= 0; i--) {
            int se = stops.get(i).depMinutes() - depBase;
            if (se < 0)
                se += 1440;
            if (elapsedMinutes(nowMin) >= se)
                return i;
        }
        return -1;
    }

    String nextStationName(int nowMin) {
        int ci = currentStopIndex(nowMin);
        if (ci < 0)
            return stops.get(0).name;
        if (ci >= stops.size() - 1)
            return "Arrived at " + destination;
        return stops.get(ci + 1).name;
    }

    String nextStationETA(int nowMin) {
        int ci = currentStopIndex(nowMin);
        if (ci < 0)
            return stops.get(0).scheduledDep;
        if (ci >= stops.size() - 1)
            return "\u2014";
        return stops.get(ci + 1).scheduledDep;
    }

    String liveArrivalETA(int nowMin) {
        int remKm = remainingKm(nowMin);
        if (remKm <= 0)
            return scheduledArrival();
        int etaMin = (nowMin + (int) Math.round((double) remKm / avgSpeedKmh * 60)) % 1440;
        return String.format("%02d:%02d", etaMin / 60, etaMin % 60);
    }

    String remainingTimeStr(int nowMin) {
        int remKm = remainingKm(nowMin);
        if (remKm <= 0)
            return "0 h 00 m";
        double h = (double) remKm / avgSpeedKmh;
        int hh = (int) h, mm = (int) Math.round((h - hh) * 60);
        return hh + " h " + String.format("%02d", mm) + " m";
    }

    @Override
    public String getCategory() {
        return "Railway";
    }

    /** Logs brief activity to train_data.txt (unchanged). */
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
        return number + " \u2013 " + name;
    }
}

// ─────────────────────────────────────────────
// TrainStore – permanent CSV persistence
//
// File: trains.csv (same folder as the .java / .class file)
// Format:
// number|name|start|destination|offDay|distance|speed
// StationName|HH:mm
// StationName|HH:mm
// (blank line)
// next train...
// ─────────────────────────────────────────────
class TrainStore {

    private static final String FILE = "trains.csv";

    /** Overwrite trains.csv with the current list of trains. */
    static void saveAll(List<Train> trains) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(FILE, false))) {
            for (Train t : trains) {
                w.write(t.number + "|" + t.name + "|" + t.start + "|"
                        + t.destination + "|" + t.offDay + "|"
                        + t.totalDistance + "|" + t.avgSpeedKmh);
                w.newLine();
                for (Station s : t.stops) {
                    w.write(s.name + "|" + s.scheduledDep);
                    w.newLine();
                }
                w.newLine(); // blank line between trains
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Load all trains from trains.csv. Returns empty list if file missing. */
    static List<Train> loadAll() {
        List<Train> result = new ArrayList<>();
        File f = new File(FILE);
        if (!f.exists())
            return result;

        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            String header = null;
            List<Station> stops = new ArrayList<>();

            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    // flush current train
                    if (header != null && stops.size() >= 2) {
                        Train t = parseHeader(header, stops);
                        if (t != null)
                            result.add(t);
                    }
                    header = null;
                    stops = new ArrayList<>();
                    continue;
                }
                if (header == null) {
                    header = line;
                } else {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2)
                        stops.add(new Station(parts[0], parts[1]));
                }
            }
            // handle file with no trailing blank line
            if (header != null && stops.size() >= 2) {
                Train t = parseHeader(header, stops);
                if (t != null)
                    result.add(t);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static Train parseHeader(String header, List<Station> stops) {
        String[] p = header.split("\\|", 7);
        if (p.length < 7)
            return null;
        try {
            return new Train(p[0], p[1], p[2], p[3], p[4],
                    Integer.parseInt(p[5]), Integer.parseInt(p[6]),
                    new ArrayList<>(stops));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

// ─────────────────────────────────────────────
// Route Progress Panel
// ─────────────────────────────────────────────
class RouteProgressPanel extends JPanel {

    private Train train;
    private int nowMin;

    RouteProgressPanel() {
        setBackground(new Color(30, 30, 30));
        setPreferredSize(new Dimension(420, 85));
    }

    void update(Train t, int n) {
        this.train = t;
        this.nowMin = n;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (train == null)
            return;
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int trackX = 20, trackY = 32, trackW = getWidth() - 40, trackH = 6;
        int stops = train.totalStops();
        int ci = train.currentStopIndex(nowMin);

        // --- NEW LOGIC: Calculate Visual Percentage ---
        double visualPct = 0.0;
        if (ci < 0) {
            visualPct = 0.0; // Hasn't departed
        } else if (ci >= stops - 1) {
            visualPct = 1.0; // Journey complete
        } else {
            // Map the time progress strictly between the current and next station's X
            // coordinates
            double basePos = (double) ci / (stops - 1);
            double nextPos = (double) (ci + 1) / (stops - 1);

            int timeCi = train.stops.get(ci).depMinutes();
            int timeNext = train.stops.get(ci + 1).depMinutes();

            int segmentDur = timeNext - timeCi;
            if (segmentDur < 0)
                segmentDur += 1440; // Handle midnight crossing

            int timeSinceCi = nowMin - timeCi;
            if (timeSinceCi < 0)
                timeSinceCi += 1440; // Handle midnight crossing

            double segmentProgress = (segmentDur == 0) ? 1.0 : (double) timeSinceCi / segmentDur;
            visualPct = basePos + (segmentProgress * (nextPos - basePos));
        }

        // Draw background track
        g.setColor(new Color(80, 80, 80));
        g.fillRoundRect(trackX, trackY, trackW, trackH, trackH, trackH);

        // Draw progress track using the new visualPct
        g.setColor(new Color(255, 215, 0));
        g.fillRoundRect(trackX, trackY, (int) (trackW * visualPct), trackH, trackH, trackH);

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));

        // Draw Station Dots & Names
        for (int i = 0; i < stops; i++) {
            double pos = (stops == 1) ? 0 : (double) i / (stops - 1);
            int cx = trackX + (int) (pos * trackW), cy = trackY + trackH / 2, r = 7;
            Color dot = (i < ci) ? new Color(34, 197, 94) : (i == ci) ? new Color(255, 215, 0) : new Color(220, 50, 50);
            g.setColor(dot);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(20, 20, 20));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(200, 200, 200));
            String lbl = train.stops.get(i).name;
            if (lbl.length() > 8)
                lbl = lbl.substring(0, 7) + "\u2026";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, cx - fm.stringWidth(lbl) / 2, trackY + trackH + 18);
        }

        // Draw Triangle Marker based on the new visualPct
        int tx = trackX + (int) (trackW * visualPct);
        g.setColor(new Color(255, 215, 0));
        g.fillPolygon(new int[] { tx - 6, tx + 6, tx }, new int[] { trackY - 12, trackY - 12, trackY - 4 }, 3);

        // Draw Legend
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        int[][] lc = { { 34, 197, 94 }, { 220, 50, 50 }, { 255, 215, 0 } };
        String[] ll = { "Passed", "Upcoming", "Current" };
        int llx = getWidth() - 205, lly = getHeight() - 8;
        for (int i = 0; i < ll.length; i++) {
            g.setColor(new Color(lc[i][0], lc[i][1], lc[i][2]));
            g.fillOval(llx + i * 70, lly - 9, 9, 9);
            g.setColor(new Color(180, 180, 180));
            g.drawString(ll[i], llx + i * 70 + 12, lly);
        }
    }
}

// ─────────────────────────────────────────────
// ADD TRAIN DIALOG
// ─────────────────────────────────────────────
class AddTrainDialog extends JDialog {

    private static final Color DARK_BG = new Color(28, 28, 36);
    private static final Color CARD_BG = new Color(38, 38, 50);
    private static final Color DARK_RED = new Color(139, 0, 0);
    private static final Color TEXT = new Color(230, 230, 230);
    private static final Color MUTED = new Color(140, 140, 160);
    private static final Font MONO = new Font("Monospaced", Font.PLAIN, 13);

    private JTextField fNumber, fName, fStart, fDest, fDist, fSpeed;
    private JComboBox<String> cbOffDay;
    private DefaultTableModel stopsModel;
    private JTable stopsTable;
    private JTextField fStopName, fStopTime;
    private Train result = null;

    AddTrainDialog(Frame owner) {
        super(owner, "Add New Train", true);
        setSize(560, 600);
        setLocationRelativeTo(owner);
        setResizable(false);
        getContentPane().setBackground(DARK_BG);
        setLayout(new BorderLayout(0, 0));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        p.setBackground(DARK_RED);
        JLabel t = new JLabel("\uD83D\uDE86  Add New Train");
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Monospaced", Font.BOLD, 15));
        p.add(t);
        return p;
    }

    private JPanel buildForm() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setBackground(DARK_BG);
        outer.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));

        JPanel infoCard = card("Train Information");
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 8));
        grid.setBackground(CARD_BG);
        fNumber = field();
        fName = field();
        fStart = field();
        fDest = field();
        fDist = field();
        fSpeed = field();
        cbOffDay = new JComboBox<>(new String[] {
                "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" });
        styleCombo(cbOffDay);
        grid.add(lbl("Train Number"));
        grid.add(fNumber);
        grid.add(lbl("Train Name"));
        grid.add(fName);
        grid.add(lbl("Origin Station"));
        grid.add(fStart);
        grid.add(lbl("Destination"));
        grid.add(fDest);
        grid.add(lbl("Distance (km)"));
        grid.add(fDist);
        grid.add(lbl("Avg Speed (km/h)"));
        grid.add(fSpeed);
        grid.add(lbl("Off Day"));
        grid.add(cbOffDay);
        ((JPanel) infoCard.getClientProperty("body")).add(grid);
        outer.add(infoCard, BorderLayout.NORTH);

        JPanel stopsCard = card("Stops  (add in order: Origin to Destination)");
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        inputRow.setBackground(CARD_BG);
        fStopName = field();
        fStopName.setPreferredSize(new Dimension(160, 28));
        fStopTime = field();
        fStopTime.setPreferredSize(new Dimension(80, 28));
        fStopTime.setToolTipText("Format: HH:mm  e.g. 07:40");
        // Enter on name -> move to time; Enter on time -> add stop
        fStopName.addActionListener(e -> fStopTime.requestFocus());
        fStopTime.addActionListener(e -> addStop());

        JButton btnAdd = actionBtn("+ Add Stop");
        JButton btnRemove = actionBtn("X Remove");
        inputRow.add(lbl("Station Name"));
        inputRow.add(fStopName);
        inputRow.add(lbl("Time (HH:mm)"));
        inputRow.add(fStopTime);
        inputRow.add(btnAdd);
        inputRow.add(btnRemove);

        stopsModel = new DefaultTableModel(new String[] { "#", "Station Name", "Departure" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        stopsTable = new JTable(stopsModel);
        styleTable(stopsTable);
        JScrollPane scroll = new JScrollPane(stopsTable);
        scroll.setPreferredSize(new Dimension(500, 130));
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 80)));

        JPanel stopsBody = (JPanel) stopsCard.getClientProperty("body");
        stopsBody.setLayout(new BorderLayout(0, 6));
        stopsBody.add(inputRow, BorderLayout.NORTH);
        stopsBody.add(scroll, BorderLayout.CENTER);
        outer.add(stopsCard, BorderLayout.CENTER);

        btnAdd.addActionListener(e -> addStop());
        btnRemove.addActionListener(e -> {
            int row = stopsTable.getSelectedRow();
            if (row >= 0) {
                stopsModel.removeRow(row);
                renumber();
            }
        });
        return outer;
    }

    private void addStop() {
        String sn = fStopName.getText().trim(), st = fStopTime.getText().trim();
        if (sn.isEmpty() || st.isEmpty()) {
            showError("Please enter both station name and time (HH:mm).");
            return;
        }
        if (!st.matches("\\d{2}:\\d{2}")) {
            showError("Time must be in HH:mm format, e.g. 07:40");
            return;
        }
        stopsModel.addRow(new Object[] { stopsModel.getRowCount() + 1, sn, st });
        fStopName.setText("");
        fStopTime.setText("");
        fStopName.requestFocus();
        int last = stopsModel.getRowCount() - 1;
        stopsTable.scrollRectToVisible(stopsTable.getCellRect(last, 0, true));
    }

    private void renumber() {
        for (int i = 0; i < stopsModel.getRowCount(); i++)
            stopsModel.setValueAt(i + 1, i, 0);
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        p.setBackground(new Color(20, 20, 28));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 50, 70)));
        JButton cancel = actionBtn("Cancel"), save = saveBtn("Save Train");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> saveTrain());
        p.add(cancel);
        p.add(save);
        return p;
    }

    private void saveTrain() {
        String number = fNumber.getText().trim(), name = fName.getText().trim();
        String start = fStart.getText().trim(), dest = fDest.getText().trim();
        String distS = fDist.getText().trim(), speedS = fSpeed.getText().trim();
        String offDay = (String) cbOffDay.getSelectedItem();
        if (number.isEmpty() || name.isEmpty() || start.isEmpty() || dest.isEmpty() || distS.isEmpty()
                || speedS.isEmpty()) {
            showError("Please fill in all train information fields.");
            return;
        }
        int dist, speed;
        try {
            dist = Integer.parseInt(distS);
        } catch (NumberFormatException ex) {
            showError("Distance must be a number.");
            return;
        }
        try {
            speed = Integer.parseInt(speedS);
        } catch (NumberFormatException ex) {
            showError("Speed must be a number.");
            return;
        }
        if (stopsModel.getRowCount() < 2) {
            showError("Please add at least 2 stops (origin + destination).");
            return;
        }
        List<Station> stops = new ArrayList<>();
        for (int i = 0; i < stopsModel.getRowCount(); i++)
            stops.add(new Station(stopsModel.getValueAt(i, 1).toString(), stopsModel.getValueAt(i, 2).toString()));
        result = new Train(number, name, start, dest, offDay, dist, speed, stops);
        dispose();
    }

    Train getResult() {
        return result;
    }

    private JPanel card(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 80)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel t = new JLabel(title);
        t.setForeground(MUTED);
        t.setFont(new Font("Monospaced", Font.PLAIN, 11));
        t.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        p.add(t, BorderLayout.NORTH);
        JPanel body = new JPanel();
        body.setBackground(CARD_BG);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        p.add(body, BorderLayout.CENTER);
        p.putClientProperty("body", body);
        return p;
    }

    private JTextField field() {
        JTextField f = new JTextField();
        f.setBackground(new Color(50, 50, 66));
        f.setForeground(TEXT);
        f.setCaretColor(Color.WHITE);
        f.setFont(MONO);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(70, 70, 90)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return f;
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("Monospaced", Font.PLAIN, 12));
        return l;
    }

    private JButton actionBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(50, 50, 66));
        b.setForeground(TEXT);
        b.setFont(new Font("Monospaced", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(80, 80, 100)),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton saveBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(DARK_RED);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Monospaced", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setBackground(new Color(50, 50, 66));
        cb.setForeground(TEXT);
        cb.setFont(MONO);
    }

    private void styleTable(JTable t) {
        t.setBackground(CARD_BG);
        t.setForeground(TEXT);
        t.setFont(new Font("Monospaced", Font.PLAIN, 12));
        t.setGridColor(new Color(60, 60, 80));
        t.setRowHeight(24);
        t.setSelectionBackground(new Color(80, 40, 40));
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setBackground(new Color(50, 50, 66));
        t.getTableHeader().setForeground(MUTED);
        t.getTableHeader().setFont(new Font("Monospaced", Font.PLAIN, 11));
        t.getColumnModel().getColumn(0).setMaxWidth(30);
        t.getColumnModel().getColumn(2).setMaxWidth(80);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input Error", JOptionPane.ERROR_MESSAGE);
    }
}

// ─────────────────────────────────────────────
// Main Application
// ─────────────────────────────────────────────
public class hello extends JFrame {

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
    private List<Train> trains;

    private static final Color DARK_RED = new Color(139, 0, 0);
    private static final Color DARK_BG = new Color(28, 28, 36);
    private static final Color CARD_BG = new Color(38, 38, 50);
    private static final Color ACCENT = new Color(255, 215, 0);
    private static final Color TEXT_MAIN = new Color(230, 230, 230);
    private static final Color TEXT_MUTED = new Color(140, 140, 160);

    public hello() {
        super("Bangladesh Train Tracker");
        loadTrains();
        buildUI();
        startLiveClock();
        trainCombo.setSelectedIndex(0);
    }

    // Load from file; if no file yet, write defaults and use them
    private void loadTrains() {
        trains = TrainStore.loadAll();
        if (trains.isEmpty()) {
            trains = new ArrayList<>(Arrays.asList(
                    new Train("754", "Silkcity Express", "Rajshahi", "Dhaka", "Sunday", 255, 52, Arrays.asList(
                            new Station("Rajshahi", "07:40"), new Station("Natore", "08:15"),
                            new Station("Baraigram", "08:45"), new Station("Ullapara", "09:10"),
                            new Station("Sirajganj", "09:40"), new Station("Jamtoil", "10:05"),
                            new Station("Solop", "10:35"), new Station("Jamtal", "11:00"),
                            new Station("Joydevpur", "12:00"), new Station("Tongi", "12:20"),
                            new Station("Airport", "12:40"), new Station("Dhaka", "13:20"))),
                    new Train("759", "Padma Express", "Dhaka", "Rajshahi", "Tuesday", 255, 55, Arrays.asList(
                            new Station("Dhaka", "23:00"), new Station("Airport", "23:25"),
                            new Station("Tongi", "23:45"), new Station("Joydevpur", "00:10"),
                            new Station("Jamtal", "00:55"), new Station("Abdulpur", "01:20"),
                            new Station("Ishwardi", "02:10"), new Station("Natore", "02:50"),
                            new Station("Rajshahi", "04:30"))),
                    new Train("773", "Kalni Express", "Sylhet", "Dhaka", "Friday", 310, 48, Arrays.asList(
                            new Station("Sylhet", "06:15"), new Station("Srimangal", "07:30"),
                            new Station("Shamshernagar", "07:55"), new Station("Bhairab", "09:00"),
                            new Station("Narsingdi", "09:30"), new Station("Tongi", "10:10"),
                            new Station("Airport", "10:30"), new Station("Tejgaon", "10:45"),
                            new Station("Kamlapur", "11:00"), new Station("Dhaka", "11:15"))),
                    new Train("705", "Subarna Express", "Dhaka", "Chittagong", "Wednesday", 320, 58, Arrays.asList(
                            new Station("Dhaka", "07:00"), new Station("Narsingdi", "07:55"),
                            new Station("Brahmanbaria", "08:40"), new Station("Akhaura", "09:05"),
                            new Station("Comilla", "09:45"), new Station("Laksham", "10:20"),
                            new Station("Feni", "11:00"), new Station("Chittagong", "12:30")))));
            TrainStore.saveAll(trains); // create trains.csv on first run
        }
    }

    private void buildUI() {
        setSize(820, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(DARK_BG);
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DARK_RED);
        p.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        JLabel title = new JLabel("\uD83D\uDE86  Bangladesh Train Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Monospaced", Font.BOLD, 18));
        p.add(title, BorderLayout.WEST);
        lblClock = new JLabel("--:--:--");
        lblClock.setForeground(new Color(255, 220, 220));
        lblClock.setFont(new Font("Monospaced", Font.PLAIN, 13));
        p.add(lblClock, BorderLayout.EAST);
        return p;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBackground(DARK_BG);
        center.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));

        JPanel top = new JPanel(new BorderLayout(8, 6));
        top.setBackground(DARK_BG);
        JPanel selectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selectorRow.setBackground(DARK_BG);

        trainCombo = new JComboBox<>(trains.toArray(new Train[0]));
        trainCombo.setBackground(CARD_BG);
        trainCombo.setForeground(TEXT_MAIN);
        trainCombo.setFont(new Font("Monospaced", Font.PLAIN, 13));
        trainCombo.setPreferredSize(new Dimension(240, 30));
        trainCombo.addActionListener(e -> refresh());

        JButton btnAdd = new JButton("+ Add Train");
        btnAdd.setBackground(DARK_RED);
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setFont(new Font("Monospaced", Font.BOLD, 12));
        btnAdd.setFocusPainted(false);
        btnAdd.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.addActionListener(e -> openAddTrainDialog());

        JButton btnDel = new JButton("X Delete");
        btnDel.setBackground(new Color(50, 50, 66));
        btnDel.setForeground(new Color(220, 80, 80));
        btnDel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        btnDel.setFocusPainted(false);
        btnDel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btnDel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDel.addActionListener(e -> deleteSelectedTrain());

        selectorRow.add(lbl("Select Train:", TEXT_MUTED, 13));
        selectorRow.add(trainCombo);
        selectorRow.add(btnAdd);
        selectorRow.add(btnDel);

        offDayBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        offDayBanner.setBackground(new Color(100, 20, 20));
        offDayBanner.setBorder(BorderFactory.createLineBorder(new Color(180, 60, 60)));
        offDayMsg = new JLabel("");
        offDayMsg.setForeground(new Color(255, 180, 180));
        offDayMsg.setFont(new Font("Monospaced", Font.PLAIN, 12));
        offDayBanner.add(offDayMsg);
        offDayBanner.setVisible(false);

        top.add(selectorRow, BorderLayout.NORTH);
        top.add(offDayBanner, BorderLayout.CENTER);
        center.add(top, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 2, 10, 0));
        cards.setBackground(DARK_BG);
        cards.add(buildDetailsCard());
        cards.add(buildLiveCard());
        center.add(cards, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBackground(DARK_BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JPanel routeCard = card("Route Progress");
        routePanel = new RouteProgressPanel();
        ((JPanel) routeCard.getClientProperty("body")).add(routePanel);
        bottom.add(routeCard, BorderLayout.CENTER);
        bottom.add(buildStatsRow(), BorderLayout.SOUTH);
        center.add(bottom, BorderLayout.SOUTH);
        return center;
    }

    private void openAddTrainDialog() {
        AddTrainDialog dlg = new AddTrainDialog(this);
        dlg.setVisible(true);
        Train t = dlg.getResult();
        if (t != null) {
            trains.add(t);
            TrainStore.saveAll(trains); // save to disk immediately
            trainCombo.addItem(t);
            trainCombo.setSelectedItem(t);
            JOptionPane.showMessageDialog(this, "  " + t.name + " added and saved permanently!", "Train Added",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteSelectedTrain() {
        Train t = (Train) trainCombo.getSelectedItem();
        if (t == null)
            return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete \"" + t.name + "\" ?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            trains.remove(t);
            TrainStore.saveAll(trains); // save to disk immediately
            trainCombo.removeItem(t);
        }
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

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(20, 20, 28));
        p.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        lblStatus = new JLabel("");
        lblStatus.setForeground(TEXT_MUTED);
        lblStatus.setFont(new Font("Monospaced", Font.PLAIN, 12));
        p.add(lblStatus, BorderLayout.WEST);
        JLabel copy = new JLabel("Copyright 2026, CSE RUET");
        copy.setForeground(TEXT_MUTED);
        copy.setFont(new Font("Monospaced", Font.PLAIN, 11));
        p.add(copy, BorderLayout.EAST);
        return p;
    }

    private void refresh() {
        Train t = (Train) trainCombo.getSelectedItem();
        if (t == null)
            return;
        LocalTime now = LocalTime.now();
        int nowMin = now.getHour() * 60 + now.getMinute() + now.getSecond() / 60;
        String today = LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        boolean isOff = today.equalsIgnoreCase(t.offDay);
        offDayBanner.setVisible(isOff);
        if (isOff)
            offDayMsg.setText("  " + t.name + " does not operate on " + t.offDay + "s.");
        lblNumber.setText(t.number);
        lblName.setText(t.name);
        lblCategory.setText(t.getCategory());
        lblOffDay.setText(t.offDay);
        lblTotalDist.setText(t.totalDistance + " km");
        lblTotalStops.setText(String.valueOf(t.totalStops()));
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
            lblLiveETA.setText("Arrived");
            lblNextStation.setText("Journey complete");
            lblNextETA.setText("--");
        } else {
            lblLiveETA.setText(t.liveArrivalETA(nowMin));
            lblNextStation.setText(t.nextStationName(nowMin));
            lblNextETA.setText(t.nextStationETA(nowMin));
        }
        int cov = t.coveredKm(nowMin), rem = t.remainingKm(nowMin);
        lblCovered.setText(cov + " km");
        lblRemaining.setText(rem + " km");
        lblRemTime.setText(prog >= 1.0 ? "Arrived" : t.remainingTimeStr(nowMin));
        lblSpeed.setText(t.avgSpeedKmh + " km/h");
        routePanel.update(t, nowMin);
        int pct = (int) Math.round(prog * 100);
        lblStatus.setText("Progress: " + pct + "%  |  " + cov + " km covered  |  " + rem + " km remaining");
        t.saveToFile();
    }

    private void startLiveClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        liveTimer = new javax.swing.Timer(1000, e -> {
            LocalDateTime now = LocalDateTime.now();
            lblClock.setText(
                    now.format(fmt) + "  (" + now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ")");
            refresh();
        });
        liveTimer.setInitialDelay(0);
        liveTimer.start();
    }

    private JPanel card(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(60, 60, 80)),
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
        p.putClientProperty("body", body);
        return p;
    }

    private void addRow(JPanel card, String labelText, JLabel valueLabel) {
        JPanel body = (JPanel) card.getClientProperty("body");
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(CARD_BG);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 70)));
        JLabel lbl = lbl(labelText, TEXT_MUTED, 12);
        lbl.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 8));
        row.add(lbl, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        body.add(row);
    }

    private JPanel statCard(String title, JLabel valueLabel, String unit) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(60, 60, 80)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        p.add(lbl(title, TEXT_MUTED, 11), BorderLayout.NORTH);
        p.add(valueLabel, BorderLayout.CENTER);
        if (!unit.isEmpty())
            p.add(lbl(unit, TEXT_MUTED, 11), BorderLayout.SOUTH);
        return p;
    }

    private JLabel infoLabel() {
        JLabel l = new JLabel("--");
        l.setForeground(TEXT_MAIN);
        l.setFont(new Font("Monospaced", Font.PLAIN, 13));
        l.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        return l;
    }

    private JLabel statLabel() {
        JLabel l = new JLabel("--");
        l.setForeground(ACCENT);
        l.setFont(new Font("Monospaced", Font.BOLD, 16));
        return l;
    }

    private static JLabel lbl(String text, Color color, int size) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(new Font("Monospaced", Font.PLAIN, size));
        return l;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            hello app = new hello();
            app.setLocationRelativeTo(null);
            app.setVisible(true);
        });
    }
}
