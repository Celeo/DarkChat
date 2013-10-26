package com.darktidegames.celeo.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;

public class Main
{

	public static void main(String[] args)
	{
		try
		{
			System.out.println("Reading from database, flushing all to a new file ...");
			Class.forName("org.sqlite.JDBC");
			File read = new File("C:/Users/Matt/DarkTide/DarkChat.db");
			File write = new File("C:/Users/Matt/DarkTide/logs.txt");
			Connection connection = DriverManager.getConnection("jdbc:sqlite:"
					+ read);
			write.delete();
			write.createNewFile();
			PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(write)));
			Statement stat = connection.createStatement();
			ResultSet rs = stat.executeQuery("Select * from `logs`");
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
			while (rs.next())
				writer.write(String.format("(%s) %s -> %s: %s\n", sdf.format(new Date(rs.getLong("time"))), rs.getString("by"), rs.getString("to"), rs.getString("message")));
			writer.write("\n\nTimestamp: "
					+ sdf.format(new Date(System.currentTimeMillis())));
			writer.flush();
			writer.close();
			rs.close();
			connection.close();
			System.out.println("Done");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

}