package io.jenkins.plugins.Controller;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import org.jenkinsci.Symbol;
import org.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.json.JSONArray;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
@Symbol("Process")
public class ProcessController implements UnprotectedRootAction {

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

    @Override
    public String getIconFileName() {
        return "Return Process";
    }

    @Override
    public String getDisplayName() {
        return "Process";
    }

    @Override
    public String getUrlName() {
        return "Process";
    }

    public HttpResponse doGetJobData(StaplerRequest req, StaplerResponse rsp) {
        String buildNumber = req.getParameter("buildNumber");

        // Check if buildNumber is provided
        if (buildNumber == null || buildNumber.isEmpty()) {
            LOGGER.severe("No build number provided in the request.");
            try {
                rsp.setStatus(400); // Bad Request
                rsp.getWriter().write("{\"error\": \"No build number provided.\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to write error response: " + e.getMessage(), e);
            }
            return null; // Indicate that we have handled the response
        }


        String sql = "SELECT build_number, job_name, result, event_start_time, event_end_time FROM process WHERE build_number = ?";
        JSONArray jobs = new JSONArray();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Set the build number parameter
            stmt.setInt(1, Integer.parseInt(buildNumber));

            // Execute the query
            ResultSet rs = stmt.executeQuery();

            // Check if the result set is empty
            if (!rs.isBeforeFirst()) { // This checks if the result set is empty
                rsp.setStatus(404); // Not Found
                rsp.getWriter().write("{\"error\": \"No job data found for build number: " + buildNumber + "\"}");
                return null; // Indicate that we have handled the response
            }

            // If there are results, continue processing
            while (rs.next()) {
                // Use a simple array to hold job data
                String[] job = new String[5];
                job[0] = rs.getString("build_number");
                job[1] = rs.getString("job_name");
                job[2] = rs.getString("result");
                job[3] = rs.getTimestamp("event_start_time") != null ? rs.getTimestamp("event_start_time").toString() : null;
                job[4] = rs.getTimestamp("event_end_time") != null ? rs.getTimestamp("event_end_time").toString() : null;

                // Add the job data to the JSON array
                JSONArray jsonJob = new JSONArray();
                for (String field : job) {
                    jsonJob.put(field);
                }
                jobs.put(jsonJob);
            }

            // Set the response content type and write the JSON output
            rsp.setContentType("application/json");
            rsp.getWriter().write(jobs.toString());
            return null; // Returning null as we have handled the response ourselves

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch job data from PostgreSQL: " + e.getMessage(), e);
            try {
                rsp.setStatus(500); // Internal Server Error
                rsp.getWriter().write("{\"error\": \"Failed to fetch job data.\"}");
            } catch (Exception innerEx) {
                LOGGER.log(Level.SEVERE, "Failed to write error response: " + innerEx.getMessage(), innerEx);
            }
            return null; // Indicate that we have handled the response
        }
    }



}
