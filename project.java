import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

/**
* Single-file Swing + SQLite application for Leave Management
*
* Usage: compile and run with sqlite-jdbc in classpath.
*
* NOTE: For a production app, split into multiple files and apply better error handling / validation.
*/
public class LeaveApp {

// --- enums matching your original logic ---
enum LeaveStatus { PENDING, APPROVED, REJECTED }
enum LeaveType { SICK, VACATION, PERSONAL, UNPAID;

public static LeaveType fromString(String s) {
try { return LeaveType.valueOf(s.toUpperCase()); }
catch (Exception e) { return null; }
}
}

// --- DB helper ---
static class DB {
private static final String DB_URL = "jdbc:sqlite:leave_system.db";
private Connection conn;

public DB() throws SQLException {
conn = DriverManager.getConnection(DB_URL);
conn.setAutoCommit(false); // <-- Set to false and LEAVE it false

// Run schema init as one single transaction
try {
initSchema();
conn.commit(); // Commit all schema changes at once
} catch (SQLException e) {
conn.rollback(); // Rollback if init fails
throw e;
}
// The line 'conn.setAutoCommit(true);' has been correctly REMOVED.
}

private void initSchema() throws SQLException {
// create tables if not exist
String createEmployees = "CREATE TABLE IF NOT EXISTS employees (" +
"id INTEGER PRIMARY KEY, name TEXT NOT NULL, department TEXT, leave_balance INTEGER NOT NULL, is_manager INTEGER NOT NULL DEFAULT 0)";
String createLeaves = "CREATE TABLE IF NOT EXISTS leave_requests (" +
"id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL, type TEXT NOT NULL, " +
"start_date TEXT, end_date TEXT, duration INTEGER NOT NULL, status TEXT NOT NULL, " +
"FOREIGN KEY(employee_id) REFERENCES employees(id))";

try (Statement st = conn.createStatement()) {
st.execute(createEmployees);
st.execute(createLeaves);
}

// seed initial employees if not exist (IDs 1,2,3 like your original)
try (PreparedStatement pst = conn.prepareStatement("SELECT COUNT(*) FROM employees WHERE id = ?")) {
pst.setInt(1, 1);
ResultSet rs = pst.executeQuery(); if (rs.next() && rs.getInt(1) == 0) addEmployee(1, "Alice", "Finance", 10, false);
pst.setInt(1, 2);
rs = pst.executeQuery(); if (rs.next() && rs.getInt(1) == 0) addEmployee(2, "Bob", "HR", 5, false);
pst.setInt(1, 3);
rs = pst.executeQuery(); if (rs.next() && rs.getInt(1) == 0) addEmployee(3, "Carol", "Management", 15, true);
}
}

public void addEmployee(int id, String name, String dept, int balance, boolean isManager) throws SQLException {
String sql = "INSERT OR IGNORE INTO employees (id, name, department, leave_balance, is_manager) VALUES (?, ?, ?, ?, ?)";
try (PreparedStatement pst = conn.prepareStatement(sql)) {
pst.setInt(1, id);
pst.setString(2, name);
pst.setString(3, dept);
pst.setInt(4, balance);
pst.setInt(5, isManager ? 1 : 0);
pst.executeUpdate();
// conn.commit(); // <-- REMOVED: The constructor will commit this
}
}

public Employee getEmployee(int id) throws SQLException {
String sql = "SELECT id, name, department, leave_balance, is_manager FROM employees WHERE id = ?";
try (PreparedStatement pst = conn.prepareStatement(sql)) {
pst.setInt(1, id);
ResultSet rs = pst.executeQuery();
if (rs.next()) {
return new Employee(
rs.getInt("id"),
rs.getString("name"),
rs.getString("department"),
rs.getInt("leave_balance"),
rs.getInt("is_manager") == 1
);
}
return null;
}
}

public Vector<Vector<Object>> getLeaveRequestsByEmployee(int empId) throws SQLException {
String sql = "SELECT id, type, start_date, end_date, duration, status FROM leave_requests WHERE employee_id = ?";
try (PreparedStatement pst = conn.prepareStatement(sql)) {
pst.setInt(1, empId);
ResultSet rs = pst.executeQuery();
Vector<Vector<Object>> rows = new Vector<>();
while (rs.next()) {
Vector<Object> r = new Vector<>();
r.add(rs.getInt("id"));
r.add(rs.getString("type"));
r.add(rs.getString("start_date"));
r.add(rs.getString("end_date"));
r.add(rs.getInt("duration"));
r.add(rs.getString("status"));
rows.add(r);
}
return rows;
}
}

public Vector<Vector<Object>> getPendingRequests() throws SQLException {
String sql = "SELECT lr.id, e.id as emp_id, e.name, lr.type, lr.duration, lr.start_date, lr.end_date " +
"FROM leave_requests lr JOIN employees e ON lr.employee_id = e.id WHERE lr.status = ?";
try (PreparedStatement pst = conn.prepareStatement(sql)) {
pst.setString(1, LeaveStatus.PENDING.name());
ResultSet rs = pst.executeQuery();
Vector<Vector<Object>> rows = new Vector<>();
while (rs.next()) {
Vector<Object> r = new Vector<>();
r.add(rs.getInt("id"));
r.add(rs.getInt("emp_id"));
r.add(rs.getString("name"));
r.add(rs.getString("type"));
r.add(rs.getInt("duration"));
r.add(rs.getString("start_date"));
r.add(rs.getString("end_date"));
rows.add(r);
}
return rows;
}
}

public int submitLeaveRequest(int empId, LeaveType type, String start, String end, int duration) throws SQLException {
String sql = "INSERT INTO leave_requests (employee_id, type, start_date, end_date, duration, status) VALUES (?, ?, ?, ?, ?, ?)";
try (PreparedStatement pst = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
pst.setInt(1, empId);
pst.setString(2, type.name());
pst.setString(3, start);
pst.setString(4, end);
pst.setInt(5, duration);
pst.setString(6, LeaveStatus.PENDING.name());
pst.executeUpdate();
ResultSet keys = pst.getGeneratedKeys();
int id = -1;
if (keys.next()) id = keys.getInt(1);
conn.commit(); // This is correct, as it's a single transaction
return id;
}
}

public LeaveRequest getLeaveRequest(int reqId) throws SQLException {
String sql = "SELECT lr.id, lr.employee_id, e.name, lr.type, lr.start_date, lr.end_date, lr.duration, lr.status, e.leave_balance " +
"FROM leave_requests lr JOIN employees e ON lr.employee_id = e.id WHERE lr.id = ?";
try (PreparedStatement pst = conn.prepareStatement(sql)) {
pst.setInt(1, reqId);
ResultSet rs = pst.executeQuery();
if (rs.next()) {
LeaveRequest lr = new LeaveRequest();
lr.id = rs.getInt("id");
lr.employeeId = rs.getInt("employee_id");
lr.employeeName = rs.getString("name");
lr.type = LeaveType.valueOf(rs.getString("type"));
lr.startDate = rs.getString("start_date");
lr.endDate = rs.getString("end_date");
lr.duration = rs.getInt("duration");
lr.status = LeaveStatus.valueOf(rs.getString("status"));
lr.employeeLeaveBalance = rs.getInt("leave_balance");
return lr;
}
return null;
}
}

public boolean processLeaveRequest(int managerId, int requestId, boolean approve) throws SQLException {
// Check manager exists and is manager
Employee m = getEmployee(managerId);
if (m == null || !m.isManager) return false;

LeaveRequest req = getLeaveRequest(requestId);
if (req == null) return false;

if (req.status != LeaveStatus.PENDING) return false;

// Using try-with-resources and a single commit at the end
// Note: In a real app, you'd wrap this whole method in a try/catch/rollback
// But for this simple app, committing at the end is fine.
if (approve) {
// check balance for non-unpaid
if (req.type != LeaveType.UNPAID && req.duration > req.employeeLeaveBalance) {
return false; // not enough balance
}
// approve and deduct
String updLeave = "UPDATE leave_requests SET status = ? WHERE id = ?";
try (PreparedStatement pst = conn.prepareStatement(updLeave)) {
pst.setString(1, LeaveStatus.APPROVED.name());
pst.setInt(2, requestId);
pst.executeUpdate();
}

if (req.type != LeaveType.UNPAID) {
String updEmp = "UPDATE employees SET leave_balance = leave_balance - ? WHERE id = ?";
try (PreparedStatement pst = conn.prepareStatement(updEmp)) {
pst.setInt(1, req.duration);
pst.setInt(2, req.employeeId);
pst.executeUpdate();
}
}
} else {
String updLeave = "UPDATE leave_requests SET status = ? WHERE id = ?";
try (PreparedStatement pst = conn.prepareStatement(updLeave)) {
pst.setString(1, LeaveStatus.REJECTED.name());
pst.setInt(2, requestId);
pst.executeUpdate();
}
}

conn.commit(); // Commit the transaction (approve/reject + balance update)
return true;
}

public int getEmployeeBalance(int empId) throws SQLException {
String sql = "SELECT leave_balance FROM employees WHERE id = ?";
try (PreparedStatement pst = conn.prepareStatement(sql)) {
pst.setInt(1, empId);
ResultSet rs = pst.executeQuery();
if (rs.next()) return rs.getInt(1);
return -1;
}
}

public void close() {
try { if (conn != null) conn.close(); } catch (Exception ignored) {}
}
}

// --- simple models for GUI usage ---
static class Employee {
int id; String name; String dept; boolean isManager; int leaveBalance;
Employee(int id, String name, String dept, int leaveBalance, boolean isManager) {
this.id = id; this.name = name; this.dept = dept; this.leaveBalance = leaveBalance; this.isManager = isManager;
}
}

static class LeaveRequest {
int id; int employeeId; String employeeName; LeaveType type; String startDate; String endDate; int duration; LeaveStatus status; int employeeLeaveBalance;
}

// --- GUI ---
private JFrame frame;
private CardLayout cards;
private JPanel cardPanel;
private DB db;

public LeaveApp() throws SQLException {
db = new DB();
SwingUtilities.invokeLater(this::buildGui);
}

private void buildGui() {
frame = new JFrame("Leave Management System");
frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
frame.setSize(800, 500);
frame.setLocationRelativeTo(null);

cards = new CardLayout();
cardPanel = new JPanel(cards);

cardPanel.add(loginPanel(), "login");
cardPanel.add(employeePanel(), "employee");
cardPanel.add(managerPanel(), "manager");

frame.setContentPane(cardPanel);
cards.show(cardPanel, "login");
frame.setVisible(true);
}

// ---------- Login Panel ----------
private JPanel loginPanel() {
JPanel p = new JPanel(new GridBagLayout());
GridBagConstraints c = new GridBagConstraints();
JLabel title = new JLabel("Leave System Login");
title.setFont(title.getFont().deriveFont(20f));
c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.insets = new Insets(10,10,20,10);
p.add(title, c);

c.gridwidth = 1; c.insets = new Insets(5,5,5,5);
c.gridy++;
p.add(new JLabel("Role:"), c);
String[] roles = {"Employee", "Manager"};
JComboBox<String> roleCombo = new JComboBox<>(roles);
c.gridx = 1; p.add(roleCombo, c);

c.gridx = 0; c.gridy++;
p.add(new JLabel("Employee ID:"), c);
JTextField idField = new JTextField(10);
c.gridx = 1; p.add(idField, c);

c.gridy++; c.gridx = 0; c.gridwidth = 2;
JButton loginBtn = new JButton("Login");
p.add(loginBtn, c);

JLabel info = new JLabel("Preloaded: Alice(id=1), Bob(id=2), Carol(id=3 manager)");
c.gridy++; p.add(info, c);

loginBtn.addActionListener(e -> {
String role = (String)roleCombo.getSelectedItem();
String idText = idField.getText().trim();
int id;
try { id = Integer.parseInt(idText); }
catch (NumberFormatException ex) { JOptionPane.showMessageDialog(frame, "Enter numeric Employee ID"); return; }

try {
Employee emp = db.getEmployee(id);
if (emp == null) { JOptionPane.showMessageDialog(frame, "Employee not found"); return; }
if ("Employee".equals(role)) {
if (emp.isManager) {
// Managers can also act as employees; that's fine
}
showEmployeeView(emp);
} else {
if (!emp.isManager) { JOptionPane.showMessageDialog(frame, "Access denied: Not a manager"); return; }
showManagerView(emp);
}
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
ex.printStackTrace();
}
});

return p;
}

// ---------- Employee Panel ----------
private JPanel empMainPanel;
private int currentEmpId;

private JPanel employeePanel() {
empMainPanel = new JPanel(new BorderLayout());
JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
JButton backBtn = new JButton("Logout");
top.add(backBtn);
empMainPanel.add(top, BorderLayout.NORTH);

// center - tabs
JTabbedPane tabs = new JTabbedPane();

// Submit tab
JPanel submit = new JPanel(new GridBagLayout());
GridBagConstraints sc = new GridBagConstraints();
sc.insets = new Insets(5,5,5,5); sc.gridx = 0; sc.gridy = 0;
submit.add(new JLabel("Leave Type:"), sc);
JComboBox<String> typeCombo = new JComboBox<>(new String[]{"SICK","VACATION","PERSONAL","UNPAID"});
sc.gridx = 1; submit.add(typeCombo, sc);

sc.gridx = 0; sc.gridy++; submit.add(new JLabel("Start Date (YYYY-MM-DD):"), sc);
JTextField startField = new JTextField(10); sc.gridx = 1; submit.add(startField, sc);

sc.gridx = 0; sc.gridy++; submit.add(new JLabel("End Date (YYYY-MM-DD):"), sc);
JTextField endField = new JTextField(10); sc.gridx = 1; submit.add(endField, sc);

sc.gridx = 0; sc.gridy++; submit.add(new JLabel("Duration (days):"), sc);
JTextField durationField = new JTextField(5); sc.gridx = 1; submit.add(durationField, sc);

sc.gridx = 0; sc.gridy++; sc.gridwidth = 2;
JButton submitBtn = new JButton("Submit Leave Request");
submit.add(submitBtn, sc);

submitBtn.addActionListener(e -> {
String t = (String)typeCombo.getSelectedItem();
String start = startField.getText().trim();
String end = endField.getText().trim();
int duration;
try { duration = Integer.parseInt(durationField.getText().trim()); }
catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Enter numeric duration"); return; }

LeaveType lt = LeaveType.fromString(t);
if (lt == null) { // Added a small check
JOptionPane.showMessageDialog(frame, "Invalid leave type somehow selected."); return;
}
if (start.isEmpty() || end.isEmpty()) { // Added validation
JOptionPane.showMessageDialog(frame, "Dates cannot be empty."); return;
}

try {
// We should also handle potential rollback on failure in submitLeaveRequest
// but for now, we just show the error.
int reqId = db.submitLeaveRequest(currentEmpId, lt, start, end, duration);
JOptionPane.showMessageDialog(frame, "Leave request submitted, ID: " + reqId);
// Clear fields
startField.setText("");
endField.setText("");
durationField.setText("");
refreshEmployeeRequests();
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
ex.printStackTrace();
}
});

// View requests tab
JPanel viewReqPanel = new JPanel(new BorderLayout());
JTable reqTable = new JTable();
JScrollPane sp = new JScrollPane(reqTable);
viewReqPanel.add(sp, BorderLayout.CENTER);

// View balance
JPanel balPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
JLabel balLabel = new JLabel("Balance: ");
balPanel.add(balLabel);

tabs.addTab("Submit Request", submit);
tabs.addTab("My Requests", viewReqPanel);
tabs.addTab("Balance", balPanel);

empMainPanel.add(tabs, BorderLayout.CENTER);

// attach actions to logout etc
backBtn.addActionListener(e -> {
cards.show(cardPanel, "login");
});

// store refs for later refresh
employeeRequestsTable = reqTable;
employeeBalanceLabel = balLabel;

return empMainPanel;
}

private JTable employeeRequestsTable;
private JLabel employeeBalanceLabel;

private void showEmployeeView(Employee emp) {
this.currentEmpId = emp.id;
frame.setTitle("Employee - ".concat(emp.name));
cards.show(cardPanel, "employee");
refreshEmployeeRequests();
refreshEmployeeBalance();
}

private void refreshEmployeeRequests() {
try {
Vector<Vector<Object>> rows = db.getLeaveRequestsByEmployee(currentEmpId);
Vector<String> cols = new Vector<>();
cols.add("ID"); cols.add("Type"); cols.add("Start"); cols.add("End"); cols.add("Duration"); cols.add("Status");
DefaultTableModel model = new DefaultTableModel(rows, cols) {
public boolean isCellEditable(int r, int c) { return false; }
};
employeeRequestsTable.setModel(model);
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
ex.printStackTrace();
}
}

private void refreshEmployeeBalance() {
try {
int bal = db.getEmployeeBalance(currentEmpId);
employeeBalanceLabel.setText("Balance: " + bal + " days");
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
ex.printStackTrace();
}
}

// ---------- Manager Panel ----------
private JPanel mgrMainPanel;
private JTable pendingTable;
private int currentMgrId;

private JPanel managerPanel() {
mgrMainPanel = new JPanel(new BorderLayout());
JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
JButton backBtn = new JButton("Logout");
top.add(backBtn);
mgrMainPanel.add(top, BorderLayout.NORTH);

// center content
JPanel center = new JPanel(new BorderLayout());
pendingTable = new JTable();
center.add(new JScrollPane(pendingTable), BorderLayout.CENTER);

// --- NEW: Add MouseListener to table for double-click ---
pendingTable.addMouseListener(new MouseAdapter() {
public void mouseClicked(MouseEvent e) {
// Check for double-click
if (e.getClickCount() == 2) {
int row = pendingTable.getSelectedRow();
if (row < 0) return; // No row selected
// Get request ID from column 0
int reqId = (int) pendingTable.getModel().getValueAt(row, 0);
// Open the new approval dialog
showApprovalDialog(reqId);
}
}
});
// --- End of new code ---


// buttons (Existing buttons are kept)
JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
JButton refreshBtn = new JButton("Refresh");
JButton approveBtn = new JButton("Approve Selected");
JButton rejectBtn = new JButton("Reject Selected");
actions.add(refreshBtn); actions.add(approveBtn); actions.add(rejectBtn);
center.add(actions, BorderLayout.SOUTH);

mgrMainPanel.add(center, BorderLayout.CENTER);

backBtn.addActionListener(e -> cards.show(cardPanel, "login"));

refreshBtn.addActionListener(e -> refreshPendingRequests());

// These buttons still work for single-selection
approveBtn.addActionListener(e -> processSelectedPending(true));
rejectBtn.addActionListener(e -> processSelectedPending(false));

return mgrMainPanel;
}

/**
* NEW METHOD: Creates a pop-up dialog to approve/reject a specific request.
* This provides more detail to the manager.
*/
private void showApprovalDialog(int reqId) {
LeaveRequest req;
try {
req = db.getLeaveRequest(reqId);
// Check if request is valid and pending
if (req == null) {
JOptionPane.showMessageDialog(frame, "Request not found (ID: " + reqId + "). It might have been processed already.");
refreshPendingRequests();
return;
}
if (req.status != LeaveStatus.PENDING) {
JOptionPane.showMessageDialog(frame, "This request is no longer pending.");
refreshPendingRequests();
return;
}
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error getting request details: " + ex.getMessage());
return;
}

// Create the dialog window
JDialog dialog = new JDialog(frame, "Process Leave Request", true); // true = modal
dialog.setLayout(new BorderLayout(10, 10));

// Panel for request details
JPanel detailsPanel = new JPanel(new GridLayout(0, 2, 5, 5)); // 0 rows, 2 cols
detailsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
detailsPanel.add(new JLabel("Request ID:"));
detailsPanel.add(new JLabel(String.valueOf(req.id)));
detailsPanel.add(new JLabel("Employee:"));
detailsPanel.add(new JLabel(req.employeeName + " (ID: " + req.employeeId + ")"));
detailsPanel.add(new JLabel("Type:"));
detailsPanel.add(new JLabel(req.type.name()));
detailsPanel.add(new JLabel("Dates:"));
detailsPanel.add(new JLabel(req.startDate + " to " + req.endDate));
detailsPanel.add(new JLabel("Duration:"));
detailsPanel.add(new JLabel(req.duration + " days"));
detailsPanel.add(new JLabel("Status:"));
detailsPanel.add(new JLabel(req.status.name()));

// Highlight the employee's current balance
detailsPanel.add(new JLabel("<html><b>Current Balance:</b></html>"));
JLabel balanceLabel = new JLabel(req.employeeLeaveBalance + " days");
if (req.type != LeaveType.UNPAID && req.duration > req.employeeLeaveBalance) {
balanceLabel.setText("<html><b style='color:red;'>" + req.employeeLeaveBalance + " days (Insufficient)</b></html>");
} else {
balanceLabel.setText("<html><b style='color:green;'>" + req.employeeLeaveBalance + " days (Sufficient)</b></html>");
}
detailsPanel.add(balanceLabel);


// Panel for buttons
JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
JButton approveBtn = new JButton("Approve");
JButton rejectBtn = new JButton("Reject");
JButton cancelBtn = new JButton("Cancel");

buttonPanel.add(approveBtn);
buttonPanel.add(rejectBtn);
buttonPanel.add(cancelBtn);

// Add action listeners for the dialog buttons
cancelBtn.addActionListener(e -> dialog.dispose());

approveBtn.addActionListener(e -> {
try {
// Re-check balance rule (same logic as processSelectedPending)
if (req.type != LeaveType.UNPAID && req.duration > req.employeeLeaveBalance) {
JOptionPane.showMessageDialog(dialog, "Cannot approve: insufficient leave balance for employee.", "Approval Error", JOptionPane.ERROR_MESSAGE);
return; // Keep dialog open for manager to see
}
// Process the approval
boolean ok = db.processLeaveRequest(currentMgrId, reqId, true);
if (ok) {
JOptionPane.showMessageDialog(frame, "Request approved successfully.");
} else {
JOptionPane.showMessageDialog(frame, "Failed to approve request (permission or already processed).");
}
dialog.dispose(); // Close dialog
refreshPendingRequests(); // Refresh main table
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
}
});

rejectBtn.addActionListener(e -> {
try {
// Process the rejection
boolean ok = db.processLeaveRequest(currentMgrId, reqId, false);
if (ok) {
JOptionPane.showMessageDialog(frame, "Request rejected successfully.");
} else {
JOptionPane.showMessageDialog(frame, "Failed to reject request (permission or already processed).");
}
dialog.dispose(); // Close dialog
refreshPendingRequests(); // Refresh main table
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
}
});

// Assemble and show dialog
dialog.add(detailsPanel, BorderLayout.CENTER);
dialog.add(buttonPanel, BorderLayout.SOUTH);
dialog.pack();
dialog.setLocationRelativeTo(frame); // Center on top of the main window
dialog.setVisible(true);
}


private void showManagerView(Employee mgr) {
this.currentMgrId = mgr.id;
frame.setTitle("Manager - ".concat(mgr.name));
cards.show(cardPanel, "manager");
refreshPendingRequests();
}

private void refreshPendingRequests() {
try {
Vector<Vector<Object>> rows = db.getPendingRequests();
Vector<String> cols = new Vector<>();
cols.add("Request ID"); cols.add("Employee ID"); cols.add("Employee"); cols.add("Type"); cols.add("Duration"); cols.add("Start"); cols.add("End");
DefaultTableModel model = new DefaultTableModel(rows, cols) {
public boolean isCellEditable(int r, int c) { return false; }
};
pendingTable.setModel(model);
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
ex.printStackTrace();
}
}

/**
* This method handles the "Approve/Reject Selected" buttons
*/
private void processSelectedPending(boolean approve) {
int row = pendingTable.getSelectedRow();
if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a request"); return; }
int reqId = (int) pendingTable.getModel().getValueAt(row, 0);
try {
LeaveRequest req = db.getLeaveRequest(reqId);
if (req == null) { JOptionPane.showMessageDialog(frame, "Request not found"); return; }

// Check balance rule for approve
if (approve && req.type != LeaveType.UNPAID && req.duration > req.employeeLeaveBalance) {
JOptionPane.showMessageDialog(frame, "Cannot approve: insufficient leave balance for employee.");
return;
}

boolean ok = db.processLeaveRequest(currentMgrId, reqId, approve);
if (!ok) {
JOptionPane.showMessageDialog(frame, "Failed to process request (permission or already processed).");
} else {
JOptionPane.showMessageDialog(frame, "Request " + (approve ? "approved" : "rejected") + " successfully.");
}
refreshPendingRequests();
} catch (SQLException ex) {
JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
ex.printStackTrace();
}
}

// ---------- main ----------
public static void main(String[] args) {
// load sqlite driver (modern JDBC may auto-load but explicit is harmless)
try {
Class.forName("org.sqlite.JDBC");
} catch (ClassNotFoundException ignored) {}

try {
new LeaveApp();
} catch (SQLException ex) {
ex.printStackTrace();
JOptionPane.showMessageDialog(null, "Failed to start DB: " + ex.getMessage());
}
}
}
