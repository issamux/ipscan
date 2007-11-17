/**
 * This file is a part of Angry IP Scanner source code,
 * see http://www.azib.net/ for more information.
 * Licensed under GPLv2.
 */
package net.azib.ipscan.gui.actions;

import java.net.InetAddress;

import net.azib.ipscan.config.GUIConfig;
import net.azib.ipscan.config.Labels;
import net.azib.ipscan.core.ScannerThread;
import net.azib.ipscan.core.ScannerThreadFactory;
import net.azib.ipscan.core.ScanningProgressCallback;
import net.azib.ipscan.core.ScanningResult;
import net.azib.ipscan.core.ScanningResultsCallback;
import net.azib.ipscan.core.ScanningResult.ResultType;
import net.azib.ipscan.core.net.PingerRegistry;
import net.azib.ipscan.core.state.ScanningState;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.core.state.StateTransitionListener;
import net.azib.ipscan.gui.ResultTable;
import net.azib.ipscan.gui.StatusBar;
import net.azib.ipscan.gui.feeders.FeederGUIRegistry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;

/**
 * Start/Stop button action class.
 * It listens to presses on the buttons as well as updates gui statuses
 * 
 * @author Anton Keks
 */
public class StartStopScanningAction implements SelectionListener, ScanningProgressCallback, StateTransitionListener {
	
	private ScannerThreadFactory scannerThreadFactory;
	private ScannerThread scannerThread;
	private GUIConfig guiConfig;
	private PingerRegistry pingerRegistry;

	private StatusBar statusBar;
	private ResultTable resultTable;
	private FeederGUIRegistry feederRegistry;
	private Button button;
	
	Image[] buttonImages = new Image[ScanningState.values().length];
	String[] buttonTexts = new String[ScanningState.values().length];
	
	private Display display;
	private StateMachine stateMachine;
	
	/**
	 * Creates internal stuff independent from all other external dependencies
	 */
	StartStopScanningAction() {
		// preload button images
		buttonImages[ScanningState.IDLE.ordinal()] = new Image(null, Labels.getInstance().getImageAsStream("button.start.img"));
		buttonImages[ScanningState.SCANNING.ordinal()] = new Image(null, Labels.getInstance().getImageAsStream("button.stop.img"));
		buttonImages[ScanningState.STARTING.ordinal()] = buttonImages[ScanningState.SCANNING.ordinal()]; 
		buttonImages[ScanningState.RESTARTING.ordinal()] = buttonImages[ScanningState.SCANNING.ordinal()];
		buttonImages[ScanningState.STOPPING.ordinal()] = new Image(null, Labels.getInstance().getImageAsStream("button.kill.img"));
		buttonImages[ScanningState.KILLING.ordinal()] = buttonImages[ScanningState.STOPPING.ordinal()];
		
		// preload button texts
		buttonTexts[ScanningState.IDLE.ordinal()] = Labels.getLabel("button.start");
		buttonTexts[ScanningState.SCANNING.ordinal()] = Labels.getLabel("button.stop");
		buttonTexts[ScanningState.STARTING.ordinal()] = buttonTexts[ScanningState.SCANNING.ordinal()]; 
		buttonTexts[ScanningState.RESTARTING.ordinal()] = buttonTexts[ScanningState.SCANNING.ordinal()];
		buttonTexts[ScanningState.STOPPING.ordinal()] = Labels.getLabel("button.kill");
		buttonTexts[ScanningState.KILLING.ordinal()] = Labels.getLabel("button.kill");
	}
	
	public StartStopScanningAction(ScannerThreadFactory scannerThreadFactory, StateMachine stateMachine, ResultTable resultTable, StatusBar statusBar, FeederGUIRegistry feederRegistry, PingerRegistry pingerRegistry, Button startStopButton, GUIConfig guiConfig) {
		this();

		this.scannerThreadFactory = scannerThreadFactory;
		this.resultTable = resultTable;
		this.statusBar = statusBar;
		this.feederRegistry = feederRegistry;
		this.pingerRegistry = pingerRegistry;
		this.button = startStopButton;
		this.display = button.getDisplay();
		this.stateMachine = stateMachine;
		this.guiConfig = guiConfig;
		
		// add listeners to all state changes
		stateMachine.addTransitionListener(this);
		
		// set the default image
		ScanningState state = stateMachine.getState();
		button.setImage(buttonImages[state.ordinal()]);
		button.setText(buttonTexts[state.ordinal()]);
	}

	/**
	 * Called when scanning button is clicked
	 */
	public void widgetDefaultSelected(SelectionEvent e) {
		widgetSelected(e);
	}

	/**
	 * Called when scanning button is clicked
	 */
	public void widgetSelected(SelectionEvent event) {
		// ask for confirmation before erasing scanning results
		if (stateMachine.inState(ScanningState.IDLE)) {
			if (!preScanChecks())
				return;
		}
		stateMachine.transitionToNext();
	}

	private final boolean preScanChecks() {
		// autodetect usable pingers and silently ignore any changes - 
		// user must see any errors only if they have explicitly selected a pinger
		pingerRegistry.checkSelectedPinger();
		
		// ask user for confirmation if needed
		if (guiConfig.askScanConfirmation && resultTable.getItemCount() > 0) {
			MessageBox box = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
			box.setText(Labels.getLabel("text.scan.new"));
			box.setMessage(Labels.getLabel("text.scan.confirmation"));
			if (box.open() != SWT.YES) {
				return false;
			}
		}
		return true;
	}
	
	public void transitionTo(final ScanningState state) {
		if (display.isDisposed())
			return;
		display.syncExec(new Runnable() {
			public void run() {
				if (statusBar.isDisposed())
					return;
				
				// TODO: separate GUI and non-GUI stuff
				switch (state) {
					case IDLE:
						// reset state text
						button.setEnabled(true);
						statusBar.setStatusText(null);
						statusBar.setProgress(0);
						break;
					case STARTING:
						// start the scan from scratch!
						resultTable.removeAll();
						try {
							scannerThread = scannerThreadFactory.createScannerThread(feederRegistry.current().getFeeder(), StartStopScanningAction.this, createResultsCallback());
							stateMachine.startScanning();
						}
						catch (RuntimeException e) {
							stateMachine.reset();
							throw e;
						}
						break;
					case RESTARTING:
						// restart the scanning - rescan
						resultTable.resetSelection();
						try {
							scannerThread = scannerThreadFactory.createScannerThread(feederRegistry.createRescanFeeder(resultTable.getSelection()), StartStopScanningAction.this, createResultsCallback());
							stateMachine.startScanning();
						}
						catch (RuntimeException e) {
							stateMachine.reset();
							throw e;
						}
						break;
					case SCANNING:
						scannerThread.start();
						break;
					case STOPPING:
						statusBar.setStatusText(Labels.getLabel("state.waitForThreads"));
						break;
					case KILLING:
						button.setEnabled(false);
						statusBar.setStatusText(Labels.getLabel("state.killingThreads"));
						break;
				}
				// change button image
				button.setImage(buttonImages[state.ordinal()]);
				button.setText(buttonTexts[state.ordinal()]);
			}
		});
	}
	
	/**
	 * @return the appropriate ResultsCallback instance, depending on the configured display method.
	 */
	private final ScanningResultsCallback createResultsCallback() {
		switch (guiConfig.displayMethod) {
			default: return new ScanningResultsCallback() {
				public void prepareForResults(ScanningResult result) {
					resultTable.addOrUpdateResultRow(result);
				}
				public void consumeResults(ScanningResult result) {
					resultTable.addOrUpdateResultRow(result);
				}
			};
			
			case ALIVE: return new ScanningResultsCallback() {
				public void prepareForResults(ScanningResult result) {
				}
				public void consumeResults(ScanningResult result) {
					if (result.getType().ordinal() >= ResultType.ALIVE.ordinal())
						resultTable.addOrUpdateResultRow(result);
				}
			};
			
			case PORTS: return new ScanningResultsCallback() {
				public void prepareForResults(ScanningResult result) {
				}
				public void consumeResults(ScanningResult result) {
					if (result.getType() == ResultType.WITH_PORTS)
						resultTable.addOrUpdateResultRow(result);
				}
			};
		}
	}

	public void updateProgress(final InetAddress currentAddress, final int runningThreads, final int percentageComplete) {
		if (display.isDisposed())
			return;
		display.asyncExec(new Runnable() {
			public void run() {
				if (statusBar.isDisposed())
					return;
				
				if (currentAddress != null) {
					statusBar.setStatusText(Labels.getLabel("state.scanning") + currentAddress.getHostAddress());
				}					

				statusBar.setRunningThreads(runningThreads);
				statusBar.setProgress(percentageComplete);

				// change button image
				button.setImage(buttonImages[stateMachine.getState().ordinal()]);
			}
		});
	}

}
