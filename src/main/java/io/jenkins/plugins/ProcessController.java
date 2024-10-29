package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ProcessController extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(ProcessController.class.getName());
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/jenkins_builds";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "1234";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.severe("PostgreSQL JDBC Driver not found: " + e.getMessage());
        }
    }

    // Triggered when the build starts
    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        String jobName = run.getParent().getFullName();
        int buildNumber = run.getNumber();
        Timestamp startTime = new Timestamp(run.getStartTimeInMillis());
        storeBuildStartData(jobName, buildNumber, startTime);
    }

    // Triggered when the build completes
    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {
        String jobName = run.getParent().getFullName();
        int buildNumber = run.getNumber();
        String result = run.getResult() != null ? run.getResult().toString() : "UNKNOWN";
        Timestamp endTime = new Timestamp(System.currentTimeMillis());
        updateBuildCompletionData(jobName, buildNumber, endTime, result);
    }

    // Method to store start data in PostgreSQL
    private void storeBuildStartData(String jobName, int buildNumber, Timestamp startTime) {
        String sql = "INSERT INTO process (job_name, build_number, event_start_time, event_end_time, result) " +
                "VALUES (?, ?, ?, NULL, NULL) " +
                "ON CONFLICT (job_name, build_number) DO NOTHING";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobName);
            stmt.setInt(2, buildNumber);
            stmt.setTimestamp(3, startTime);
            stmt.executeUpdate();
            LOGGER.info("Build start data stored successfully in PostgreSQL.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to store build start data in PostgreSQL: " + e.getMessage(), e);
        }
    }

    // Method to update completion data in PostgreSQL
    private void updateBuildCompletionData(String jobName, int buildNumber, Timestamp endTime, String result) {
        String sql = "UPDATE process SET event_end_time = ?, result = ? WHERE job_name = ? AND build_number = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, endTime);
            stmt.setString(2, result);
            stmt.setString(3, jobName);
            stmt.setInt(4, buildNumber);
            stmt.executeUpdate();
            LOGGER.info("Build completion data updated successfully in PostgreSQL.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update build completion data in PostgreSQL: " + e.getMessage(), e);
        }
    }
}
