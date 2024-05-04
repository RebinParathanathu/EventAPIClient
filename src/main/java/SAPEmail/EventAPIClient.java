package SAPEmail;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;

public class EventAPIClient {

    private static final Logger logger = Logger.getLogger(EventAPIClient.class.getName());

    public static void main(String[] args) throws IOException {
        // Configure the logger
        configureLogger();
     // Read API Request from file
        Map<String, String> apiDetails = readApiDetailsFromFile("api_request.txt");
     // Make API request
		for (int i = 1; apiDetails.containsKey("query" + i); i++) {
		    String apiUrl = apiDetails.get("apiUrl");
		    String apiKey = apiDetails.get("apiKey");
		    String accountName = apiDetails.get("accountName");
		    String query = apiDetails.get("query"+i);
		    String senderEmail = apiDetails.get("senderEmail");
		    String senderPassword = apiDetails.get("senderPassword");
		    
		    try {
		        HttpClient httpClient = HttpClients.createDefault();
		        HttpPost httpPost = new HttpPost(apiUrl);
		
		        httpPost.addHeader("X-Events-API-AccountName", accountName);
		        httpPost.addHeader("X-Events-API-Key", apiKey);
		        httpPost.addHeader("Content-type", "application/vnd.appd.events+text;v=2");
		
		        httpPost.setEntity(new StringEntity(query));
		
		        HttpResponse response = httpClient.execute(httpPost);
		
		        // Process API response
		        String jsonResponse = processResponse(response);
		
		        // Specify the file path for the CSV file
		        String csvFilePath = "users.csv";
		
		        // Parse JSON response and compare data
		        compareAndOutputResults(jsonResponse, csvFilePath, senderEmail, senderPassword);
		    } catch (IOException e) {
		        // Log the exception
		        logger.log(Level.SEVERE, "Exception occurred", e);
		    }
		}
    }

    private static void configureLogger() {
        try {
            // Create a file handler for the logger
            FileHandler fileHandler = new FileHandler("EventAPIClient.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            
            // Add the file handler to the logger
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            // Log the exception if configuring the logger fails
            logger.log(Level.SEVERE, "Error configuring logger", e);
        }
    }
    
    private static Map<String, String> readApiDetailsFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        // Read lines from the file and collect them into a Map
        return Files.lines(path)
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1].trim()));
    }

    private static String processResponse(HttpResponse response) throws IOException {
        // Check if the response is successful (status code 2xx)
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            // Extract the response content as a string
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity);
        } else {
            // Handle unsuccessful response (e.g., log error, throw exception)
            String errorMessage = "Error: HTTP " + statusCode + " " + response.getStatusLine().getReasonPhrase();
            logger.log(Level.SEVERE, errorMessage);
            return null;
        }
    }

    private static void compareAndOutputResults(String jsonResponse, String csvFilePath, String senderEmail, String senderPassword) throws IOException {
        // Parse JSON response
        JSONArray jsonArray = new JSONArray(jsonResponse);

        // Check if the "results" array exists
        if (jsonArray.length() > 0 && jsonArray.getJSONObject(0).has("results")) {
            // Get the "results" array
            JSONArray resultsArray = jsonArray.getJSONObject(0).getJSONArray("results");
            logger.log(Level.INFO, "resultsArray:\n" + resultsArray);

            // Map to store recipient email addresses for each createdByValue
            Map<String, List<String>> recipientEmailsMap = new HashMap<>();
            String jobName = null;
            String errorMessage = null;
            String errorTime = null;
            // Iterate through each result in the "results" array
            for (int i = 0; i < resultsArray.length(); i++) {
                // Extract CREATED_BY value from the current result
                String createdByValue = resultsArray.getJSONArray(i).getString(1);
                jobName = resultsArray.getJSONArray(i).getString(2);
                errorMessage = resultsArray.getJSONArray(i).getString(3);
                errorTime = resultsArray.getJSONArray(i).getString(0);

                // Initialize recipient email list if not already present
                recipientEmailsMap.putIfAbsent(createdByValue, new ArrayList<>());

                // Iterate through CSV records
                Iterator<CSVRecord> csvIterator = null;
                try {
                    csvIterator = CSVParser.parse(Files.newBufferedReader(Paths.get(csvFilePath)), CSVFormat.DEFAULT).iterator();
                } catch (IOException e) {
                    // Log the exception if CSV parsing fails
                    logger.log(Level.SEVERE, "Error parsing CSV", e);
                }

                while (csvIterator != null && csvIterator.hasNext()) {
                    CSVRecord record = csvIterator.next();
                    // Assuming the CREATED_BY values are in the second column (index 1)
                    String csvCreatedBy = record.get(0);

                    // Check if the CREATED_BY value matches
                    if (createdByValue.equals(csvCreatedBy)) {
                        // Iterate over all remaining columns starting from the second one
                        for (int columnIndex = 1; columnIndex < record.size(); columnIndex++) {
                            String recipientEmail = record.get(columnIndex);
                            recipientEmailsMap.get(createdByValue).add(recipientEmail);
                            logger.log(Level.INFO, "Added recipient email " + recipientEmail + " for createdByValue " + createdByValue);
                        }
                    }
                }
            }
            
         // Log contents of recipientEmailsMap
            for (Map.Entry<String, List<String>> entry : recipientEmailsMap.entrySet()) {
                String createdByValue = entry.getKey();
                List<String> recipientEmails = entry.getValue();
                logger.log(Level.INFO, "Created by value: " + createdByValue + ", Recipient emails: " + recipientEmails);
            }

         // Send emails for each createdByValue
            for (Map.Entry<String, List<String>> entry : recipientEmailsMap.entrySet()) {
                String createdByValue = entry.getKey();
                List<String> recipientEmails = entry.getValue();
                //System.out.println("recipientEmails: " + recipientEmails);
                //for (String recipientEmail : recipientEmails) {
                	//System.out.println("recipientEmail: " + recipientEmail);
                	//List<String> recipientEmailss = recipientEmails;
                sendEmail(senderEmail, senderPassword, recipientEmails, createdByValue, jobName, errorMessage, errorTime);
                //}
            }
        } else {
            logger.log(Level.SEVERE, "Error: No 'results' array found in the JSON response.");
        }
    }

    
    private static void sendEmail(String senderEmail, String senderPassword, List<String> recipientEmails, String createdByValue, String jobName, String errorMessage, String errorTime) throws IOException {
    	Map<String, String> MailProperties = readApiDetailsFromFile("mail.txt");
        // Set up JavaMail properties
        Properties properties = new Properties();
        properties.putAll(MailProperties);
        //properties.put("mail.smtp.auth", String.valueOf(MailProperties.get("smtpAuth")));
        //properties.put("mail.smtp.starttls.enable", String.valueOf(MailProperties.get("smtpSecure")));
        //properties.put("mail.smtp.host", MailProperties.get("smtpHost"));
        //properties.put("mail.smtp.port", MailProperties.get("smtpPort"));
        
     // Declare session variable
        Session session = null;

     // Check if authentication is required
        if (Boolean.parseBoolean(MailProperties.get("mail.smtp.auth"))) {
            // Create a Session with authentication
            session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, senderPassword);
                }
            });
            
        } else {
            // Create a Session without authentication
            session = Session.getInstance(properties);
        }

        try {
        	// Read HTML email template file
            String templatePath = "email_template.html";
            String template = readFileAsString(templatePath);
            template = template.replace("${jobName}", jobName);
            String subjectFromTemplate = extractSubjectFromTemplate(template);
            String htmlBody = template.replace("${subject}", subjectFromTemplate)
                                      .replace("${errorTime}", errorTime)
                                      .replace("${jobName}", jobName)
                                      .replace("${errorMessage}", errorMessage)
                                      .replace("${createdByValue}", createdByValue);
          
            // Create a MimeMessage object
            Message message = new MimeMessage(session);

            // Set the sender's email address
            message.setFrom(new InternetAddress(senderEmail));

            // Set the recipient's email address
            //message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            for (String recipientEmail : recipientEmails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
            }
         
            // Set the email subject
            //message.setSubject("Critical Alert: " + jobName);
            
            message.setSubject(subjectFromTemplate);

            // Set the email content (body)
            //message.setText("Critical Alert Triggered at " +errorTime + " for " +jobName+ " with this error message: " +errorMessage);
            message.setContent(htmlBody, "text/html; charset=utf-8");
            // Send the email
            Transport.send(message);

            logger.log(Level.INFO, "Email sent successfully!");

        } catch (MessagingException e) {
            e.printStackTrace();
            //System.err.println("Error sending email: " + e.getMessage());
            logger.log(Level.SEVERE, "Error sending email: ", e);
        }
    }
    private static String extractSubjectFromTemplate(String template) {
        // Extract the subject from the HTML template title
        int startIndex = template.indexOf("<title>");
        int endIndex = template.indexOf("</title>", startIndex);
        if (startIndex != -1 && endIndex != -1) {
            String titleLine = template.substring(startIndex, endIndex);
            // Remove HTML tags and get the subject value
            String subject = titleLine.replaceAll("\\<.*?\\>", "").trim();
            return subject;
        }
        return "Critical Alert"; // Default subject if not found
    }
    private static String readFileAsString(String filePath) throws IOException {
        byte[] encodedBytes = Files.readAllBytes(Paths.get(filePath));
        return new String(encodedBytes, StandardCharsets.UTF_8);
    }
}
