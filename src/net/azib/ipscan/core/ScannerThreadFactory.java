/**
 * This file is a part of Angry IP Scanner source code,
 * see http://www.azib.net/ for more information.
 * Licensed under GPLv2.
 */
package net.azib.ipscan.core;

import net.azib.ipscan.config.ScannerConfig;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.feeders.Feeder;

/**
 * ScannerThreadFactory.
 * 
 * Note: setter injection is used for this class to avoid cyclic dependency conflicts.
 *
 * @author Anton Keks
 */
public class ScannerThreadFactory {
	
	private ScanningResultList scanningResults;
	private Scanner scanner;
	private StateMachine stateMachine;
	private ScannerConfig scannerConfig;
	
	public ScannerThreadFactory(ScanningResultList scanningResults, Scanner scanner, StateMachine stateMachine, ScannerConfig scannerConfig) {
		this.scanningResults = scanningResults;
		this.scanner = scanner;
		this.stateMachine = stateMachine;
		this.scannerConfig = scannerConfig;
	}

	public ScannerThread createScannerThread(Feeder feeder, ScanningProgressCallback progressCallback, ScanningResultsCallback resultsCallback) {
		return new ScannerThread(feeder, scanner, stateMachine, progressCallback, scanningResults, scannerConfig, resultsCallback);
	}
}
