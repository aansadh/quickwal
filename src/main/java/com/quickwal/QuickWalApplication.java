package com.quickwal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Provides the QuickWalApplication class used by QuickWal.
 */

@SpringBootApplication
public class QuickWalApplication {

	/**
	 * Starts the QuickWal application.
	 * @param args input value
	 */
	public static void main(String[] args) {
		SpringApplication.run(QuickWalApplication.class, args);
	}
}
