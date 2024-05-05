# Email Notification For SAP Job Failure

Send email notifications to the actual SAP Job owners whenever there is a failure of Job.

We had to create this project/program because in AppDynamics we need to create policies where we can send email notification incase of any event. Here during the policy creation we define the email addresses where notification needs to be sent. 

SAP jobs can be created by 'n' number of people. And the email addresses are not available. so we cannot define this in policies or custom alerts in AppDynamics.

## Pre-Requisites

SAP application is monitored by AppDynamics ABAP agent and Jobs & Users related metrics are send to AppDynamics Event Service. We can query it in the analytics.

Connectivity to AppDynamics Event Service is required. JRE or JDK is required to run the jar file.

## Features

Java source code, supported files and jar file is shared here. 
User can use the same jar file and run directly in any server (windows/unix) using below command

java -jar EventAPIClient.jar

## Usage

Step 1: api_request.txt

Use the correct Event Service API URL, API Key, Account name from AppDynamics.
Use Sender email and password (if required or else keep it blank)

We can use multiple queries to query, but the variable should be query1, query2 and so on

Make sure to create the query in same format - eventTimestamp - first element, CREATED_BY - second element, JOBNAME - Third element, ERROR_MSG - Fourth element.

Here the most important is CREATED_BY - so this should always be usename which we need to compare in the users.csv file. Here logic is - the usename captured from the analytics is compared in the CSV file and get the mapped or associated email ID from the Users.csv file

Step 2: email_template.html

You can customize the email as per your requirements. It accepts all html tags. And also you can use predifined variables ${jobName}, ${createdByValue}, ${errorMessage}. This comes from the analytics data defined in the query. So sequence is important which I have explained in Step 1

Step 3: mail.txt

It is standard. You can use it accordingly.

Step 4: users.csv

If you open it it excel - First Column should be username and second column should be the email address. If you have multiple emails then add in columu 3, column 4 and so on. 

if you open it in textfile then it is csv which is comma separated. so the format would be 
username,emailaddress1,emailaddress2 and so on

Step 5: EventAPIClient.log

Logger is defined which will log the results, you can troubleshoot if you face any error or any step not working

## Logic

This is only for you to understand.

It reads the API details from api_request.txt. Execute the https request and stores the response in json.

It parse the json response from the result and stores it string variable.

It checks the username (received in json response) in uses.csv file and if it matches then picks the mapped email addresses from csv.

Once it has email address it sends email as critical notification to the user.



