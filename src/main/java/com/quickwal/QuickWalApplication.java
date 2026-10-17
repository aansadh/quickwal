package com.quickwal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Provides the QuickWalApplication class used by QuickWal.
 */

@SpringBootApplication
@EnableScheduling
public class QuickWalApplication {

	/**
	 * Starts the QuickWal application.
	 * @param args input value
	 */
	public static void main(String[] args) {
		SpringApplication.run(QuickWalApplication.class, args);
	}
}
