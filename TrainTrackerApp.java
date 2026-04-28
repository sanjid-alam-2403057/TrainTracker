import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

abstract class Transport {
    abstract String getCategory();
}

interface Persistable {
    void saveToFile();
}

class Station {
    String name;
    boolean reached;

    Station(String name, boolean reached) {
        this.name = name;
        this.reached = reached;
    }
}

class Train extends Transport implements Persistable {
    String number, name, start, departure, destination, arrival, nextStation, nextStop, offDay;
    int totalStops, totalDistance, remainingDist;
    String remainingTime;
    List<Station> stops;

    Train(String number, String name, String start, String departure, String destination,
            String arrival, int totalStops, String nextStation, String nextStop,
            int totalDistance, int remainingDist, String remainingTime, String offDay) {
        this.number = number;
        this.name = name;
        this.start = start;
        this.departure = departure;
        this.destination = destination;
        this.arrival = arrival;
        this.totalStops = totalStops;
        this.nextStation = nextStation;
        this.nextStop = nextStop;
        this.totalDistance = totalDistance;
        this.remainingDist = remainingDist;
        this.remainingTime = remainingTime;
        this.offDay = offDay;

        this.stops = new ArrayList<>();
        for (int i = 0; i < totalStops; i++) {
            stops.add(new Station("Stop " + (i + 1), i < (totalStops / 2)));
        }
    }

    @Override
    String getCategory() {
        return "Railway";
    }

    @Override
    public void saveToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("train_data.txt", true))) {
            writer.write(number + "," + name + "," + start + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return number + "-" + name;
    }
}

public class TrainTrackerApp extends JFrame {
    private JComboBox<Train> trainCombo;
    private JPanel infoPanel, progressPanel;
    private JLabel[] labels = new JLabel[10];
    private JLabel statusLabel, timeLabel;

    public TrainTrackerApp() {
        setTitle("Bangladesh Train Tracker");
        setSize(700, 450);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel header = new JPanel();
        header.setBackground(new Color(139, 0, 0));
        JLabel title = new JLabel("Bangladesh Train Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        header.add(title);
        add(header, BorderLayout.NORTH);

        JPanel mainBody = new JPanel(new GridLayout(1, 2));
        mainBody.setBackground(new Color(200, 210, 180));

        JPanel leftPanel = new JPanel(null);
        leftPanel.setOpaque(false);
        JLabel selectLabel = new JLabel("Select Train");
        selectLabel.setBounds(20, 20, 100, 20);
        leftPanel.add(selectLabel);

        Train silkcity = new Train("754", "Silkcity Express", "Rajshahi", "07:40", "Dhaka",
                "13:20", 12, "Solop", "Jamtal", 255, 126, "02:50", "Sunday");

        trainCombo = new JComboBox<>(new Train[] { silkcity });
        trainCombo.setBounds(20, 50, 180, 30);
        leftPanel.add(trainCombo);

        LocalDateTime now = LocalDateTime.now();
        String currentDay = now.getDayOfWeek().name();
        timeLabel = new JLabel("System: " + now.format(DateTimeFormatter.ofPattern("HH:mm")) + " (" + currentDay + ")");
        timeLabel.setBounds(20, 320, 200, 20);
        leftPanel.add(timeLabel);

        JLabel copyright = new JLabel("Copyright@2026, CSE RUET");
        copyright.setBounds(20, 350, 200, 20);
        leftPanel.add(copyright);

        infoPanel = new JPanel();
        infoPanel.setBackground(new Color(44, 62, 80));
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 10));

        for (int i = 0; i < 10; i++) {
            labels[i] = new JLabel();
            labels[i].setForeground(Color.CYAN);
            labels[i].setFont(new Font("Monospaced", Font.PLAIN, 13));
            infoPanel.add(labels[i]);
            infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        progressPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.GRAY);
                g.fillRect(20, 15, 280, 8);
                g.setColor(Color.YELLOW);
                g.fillRect(20, 15, 140, 8);

                for (int i = 0; i < 12; i++) {
                    g.setColor(i < 6 ? Color.GREEN : Color.RED);
                    g.fillOval(20 + (i * 24), 12, 12, 12);
                }
            }
        };
        progressPanel.setPreferredSize(new Dimension(300, 60));
        progressPanel.setBackground(new Color(60, 63, 65));
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setForeground(Color.WHITE);

        infoPanel.add(progressPanel);
        infoPanel.add(statusLabel);

        mainBody.add(leftPanel);
        mainBody.add(infoPanel);
        add(mainBody, BorderLayout.CENTER);

        trainCombo.addActionListener(e -> updateDisplay(currentDay));
        updateDisplay(currentDay);
    }

    private void updateDisplay(String currentDay) {
        Train t = (Train) trainCombo.getSelectedItem();
        if (t == null)
            return;

        if (currentDay.equalsIgnoreCase(t.offDay)) {
            JOptionPane.showMessageDialog(this, "Train is OFF today!");
        }

        String[] data = {
                "1. Train Number:    " + t.number,
                "2. Train Name:      " + t.name,
                "3. Start Station:   " + t.start,
                "4. Departure Time:  " + t.departure,
                "5. Destination:     " + t.destination,
                "6. Arrival Time:    " + t.arrival,
                "7. No. of Stops:    " + t.totalStops,
                "8. Next Station:    " + t.nextStation,
                "9. Next Stop:       " + t.nextStop,
                "10. Total Distance: " + t.totalDistance + " km"
        };

        for (int i = 0; i < 10; i++)
            labels[i].setText(data[i]);
        statusLabel.setText("Remaining " + t.remainingDist + " km in " + t.remainingTime + " hours");

        t.saveToFile();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TrainTrackerApp().setVisible(true));
    }
}
