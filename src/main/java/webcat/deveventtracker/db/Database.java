/**
 * 
 */
package main.java.webcat.deveventtracker.db;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import main.java.webcat.deveventtracker.models.Assignment;
import main.java.webcat.deveventtracker.models.CurrentFileSize;
import main.java.webcat.deveventtracker.models.SensorData;
import main.java.webcat.deveventtracker.models.StudentProject;
import main.java.webcat.deveventtracker.models.metrics.EarlyOften;

/**
 * Singleton class providing restricted access to the database.
 * 
 * @author Ayaan Kazerouni
 * @version 2018-09-13
 */
public class Database {

    /**
     * The singleton instance
     */
    private static Database theInstance;

    private Connection connect;

    private Database() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            String user = System.getProperty("mysql.user");
            String pw = System.getProperty("mysql.pw");
            this.connect = DriverManager.getConnection("jdbc:mysql://localhost/web-cat-dev?" + "user=" + user + "&"
                    + "password=" + pw + "&" + "serverTimezone=UTC");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return a singleton instance of this class, creating it if necessary
     */
    public static Database getInstance() {
        if (theInstance == null) {
            try {
                theInstance = new Database();
            } catch (SQLException e) {
                System.out.println("An error occurred while getting the MySQL connection:\n" + e.getMessage());
            }
        }

        return theInstance;
    }

    /**
     * Gets the newest {@link SensorData} events for the specified student for the
     * specified assignment. "New" events are those that come after the given
     * {@code afterTime}.
     * 
     * @param project   The {@link StudentProject} for which we want new events
     * @param afterTime A time stamp in milliseconds
     * @return An array of {@link SensorData} events
     */
    public SensorData[] getNewEventsForStudentOnAssignment(StudentProject project, long afterTime) {
        List<SensorData> events = new ArrayList<SensorData>();
        ResultSet result = null;
        String sdQuery = "select SensorData.`time`, SensorDataProperty.value as 'className', SensorData.currentSize as currentSize"
                + "from SensorData, SensorDataProperty, StudentProject, StudentProjectForAssignment, ProjectForAssignment, TASSIGNMENTOFFERING "
                + "where SensorData.projectId = StudentProject.OID " + "and SensorDataProperty.name = 'Class-Name' "
                + "and SensorDataProperty.sensorDataId = SensorData.OID "
                + "and StudentProject.OID = StudentProjectForAssignment.studentProjectId "
                + "and StudentProjectForAssignment.projectForAssignmentId = ProjectForAssignment.OID "
                + "and ProjectForAssignment.assignmentOfferingId = TASSIGNMENTOFFERING.OID "
                + "and SensorData.userId = ? " + "and TASSIGNMENTOFFERING.OID = ? " + "and SensorData.`time` >= ?;";
        try {
            PreparedStatement preparedStatement = this.connect.prepareStatement(sdQuery);
            preparedStatement.setString(1, project.getUserId());
            preparedStatement.setString(2, project.getAssignment().getAssignmentId());
            preparedStatement.setDate(3, new Date(afterTime));
            result = preparedStatement.executeQuery();
            while (result.next()) {
                SensorData event = new SensorData(result.getLong("time"), result.getInt("currentSize"),
                        result.getString("className"));
                events.add(event);
            }
        } catch (SQLException e) {
            System.out.println("An exception occured while retrieving SensorData.");
            return null;
        } finally {
            this.close(result);
        }
        return events.toArray(new SensorData[events.size()]);
    }

    /**
     * Get the specified TASSIGNMENTOFFERING from Web-CAT.
     * 
     * @param assignmentOfferingId The TASSIGNMENTOFFERING.OID
     * @return An {@link Assignment}
     * @throws IllegalArgumentException if there is no assignment offering with the
     *                                  specified id.
     */
    public Assignment getAssignment(String assignmentOfferingId) {
        String query = "select OID as assignmentId, CDUEDATE as deadline " + "from TASSIGNMENTOFFERING "
                + "where OID = ?";
        ResultSet result = null;
        try {
            PreparedStatement preparedStatement = this.connect.prepareStatement(query);
            preparedStatement.setString(1, assignmentOfferingId);
            result = preparedStatement.executeQuery();
            if (result.first()) {
                return new Assignment(result.getString("assignmentId"), result.getLong("deadline"));
            } else {
                throw new IllegalArgumentException(
                        "Couldn't find an assignment offering with id=" + assignmentOfferingId);
            }
        } catch (SQLException e) {
            System.out.println("An exception occured while retrieving the assigment.");
            return null;
        } finally {
            this.close(result);
        }
    }

    public StudentProject getStudentProject(String userId, Assignment assignment) {
        String query = "select FileSizeForStudentProject.name as className, FileSizeForStudentProject.size as currentSize, "
                + "EarlyOftenForStudentProject.* "
                + "from IncDevFeedbackForStudentProject, EarlyOftenForFeedback, FileSizeForStudentProject "
                + "where IncDevFeedbackForStudentProject.earlyOftenId = EarlyOftenForFeedback.id "
                + "and FileSizeForStudentProject.feedbackId = IncDevFeedbackForStudentProject.id "
                + "and IncDevFeedbackForStudentProject.userId = ? and IncDevFeedbackForStudentProject.assignmentOfferingId = ?";
        ResultSet result = null;
        try {
            PreparedStatement preparedStatement = this.connect.prepareStatement(query);
            preparedStatement.setString(1, userId);
            preparedStatement.setString(2, assignment.getAssignmentId());
            result = preparedStatement.executeQuery();
            Map<String, CurrentFileSize> fileSizes = new HashMap<String, CurrentFileSize>();
            if (result.first()) {
                // The early often score will be replicated for each file entry
                EarlyOften earlyOften = new EarlyOften(result.getInt("totalEdits"), result.getInt("totalWeightedEdits"),
                        result.getDouble("score"), result.getLong("lastUpdated"));
                do {
                    // We moved to the first one already, so read it before moving the cursor
                    // further
                    fileSizes.put(result.getString("className"),
                            new CurrentFileSize(result.getString("className"), result.getInt("currentSize")));
                } while (result.next());

                StudentProject project = new StudentProject(userId, assignment, fileSizes, earlyOften);
                return project;
            } else {
                // We haven't seen this project before.
                // TODO: Create new and return
                return null;
            }

        } catch (SQLException e) {
            System.out.println("An error occurred while retrieving the feedback object.");
            return null;
        } finally {
            this.close(result);
        }
    }

    private void close(ResultSet result) {
        try {
            if (result != null) {
                result.close();
            }
        } catch (SQLException e) {
            System.out.println("An error occured while closing resources.");
        }
    }
}