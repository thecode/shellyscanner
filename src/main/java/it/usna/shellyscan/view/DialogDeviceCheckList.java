package it.usna.shellyscan.view;

import static it.usna.shellyscan.Main.LABELS;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import it.usna.shellyscan.controller.UsnaAction;
import it.usna.shellyscan.model.Devices;
import it.usna.shellyscan.model.Devices.EventType;
import it.usna.shellyscan.model.device.BatteryDeviceInterface;
import it.usna.shellyscan.model.device.ShellyAbstractDevice;
import it.usna.shellyscan.model.device.ShellyAbstractDevice.Status;
import it.usna.shellyscan.model.device.g1.AbstractG1Device;
import it.usna.shellyscan.model.device.g2.AbstractG2Device;
import it.usna.shellyscan.model.device.g2.RangeExtenderManager;
import it.usna.shellyscan.model.device.g2.WIFIManagerG2;
import it.usna.shellyscan.view.util.Msg;
import it.usna.shellyscan.view.util.UtilCollecion;
import it.usna.swing.UsnaPopupMenu;
import it.usna.swing.table.ExTooltipTable;
import it.usna.swing.table.UsnaTableModel;
import it.usna.util.UsnaEventListener;

public class DialogDeviceCheckList extends JDialog implements UsnaEventListener<Devices.EventType, Integer> {
	private static final long serialVersionUID = 1L;
	private final static Logger LOG = LoggerFactory.getLogger(AbstractG1Device.class);
	private final static String TRUE = LABELS.getString("true_yn");
	private final static String FALSE = LABELS.getString("false_yn");
	private final static int COL_STATUS = 0;
	private final static int COL_NAME = 1;
	private final static int COL_IP = 2;
	private final static int COL_ECO = 3;
	private final static int COL_LED = 4;
	private final static int COL_BLE = 6;
	private final static int COL_AP = 7;
	private final static int COL_EXTENDER = 11;
	
	private Devices appModel;
	private int[] devicesInd;
	private ExTooltipTable table;
	private UsnaTableModel tModel;
	private ExecutorService exeService /*= Executors.newFixedThreadPool(20)*/;

	public DialogDeviceCheckList(final Window owner, Devices appModel, int[] devicesInd, final SortOrder ipSort) {
		super(owner, LABELS.getString("dlgChecklistTitle"));
		this.appModel = appModel;
		this.devicesInd = devicesInd;

		BorderLayout borderLayout = (BorderLayout) getContentPane().getLayout();
		borderLayout.setVgap(2);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		tModel = new UsnaTableModel("",
				LABELS.getString("col_device"), LABELS.getString("col_ip"), LABELS.getString("col_eco"), LABELS.getString("col_ledoff"), LABELS.getString("col_logs"),
				LABELS.getString("col_blt"), LABELS.getString("col_AP"), LABELS.getString("col_roaming"), LABELS.getString("col_wifi1"), LABELS.getString("col_wifi2"), LABELS.getString("col_extender")) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(final int c) {
				if(c == COL_IP) {
					return InetAddressAndPort.class;
				} else {
					return super.getColumnClass(c);
				}
			}
		};
		
		table = new ExTooltipTable(tModel, true) {
			private static final long serialVersionUID = 1L;
			{
				columnModel.getColumn(COL_STATUS).setMaxWidth(DevicesTable.ONLINE_BULLET.getIconWidth() + 4);
				setHeadersTooltip(LABELS.getString("col_status_exp"), null, null, LABELS.getString("col_eco_tooltip"), LABELS.getString("col_ledoff_tooltip"), LABELS.getString("col_logs_tooltip"), 
						LABELS.getString("col_blt_tooltip"), LABELS.getString("col_AP_tooltip"), LABELS.getString("col_roaming_tooltip"), LABELS.getString("col_wifi1_tooltip"), LABELS.getString("col_wifi2_tooltip"), LABELS.getString("col_extender_tooltip"));

				TableCellRenderer rendTrueOk = new CheckRenderer(true);
				TableCellRenderer rendFalseOk = new CheckRenderer(false);
				columnModel.getColumn(COL_ECO).setCellRenderer(rendTrueOk);
				columnModel.getColumn(COL_LED).setCellRenderer(rendTrueOk);
				columnModel.getColumn(5).setCellRenderer(rendFalseOk); // logs
				columnModel.getColumn(COL_BLE).setCellRenderer(rendFalseOk);
				columnModel.getColumn(COL_AP).setCellRenderer(rendFalseOk);
				columnModel.getColumn(8).setCellRenderer(rendFalseOk); // roaming
				columnModel.getColumn(9).setCellRenderer(rendTrueOk); // wifi1 null -> "-"
				columnModel.getColumn(10).setCellRenderer(rendTrueOk); // wifi2 null -> "-"
				columnModel.getColumn(11).setCellRenderer(rendTrueOk); // extender null -> "-"

				((TableRowSorter<?>)getRowSorter()).setSortsOnUpdates(true);
				
				if(ipSort != SortOrder.UNSORTED) {
					sortByColumn(COL_IP, ipSort);
				}
			}
		};

		fill();

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(table);
		getContentPane().add(scrollPane, BorderLayout.CENTER);

		table.setRowHeight(table.getRowHeight() + 3);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JPanel panelBottom = new JPanel(new BorderLayout(0, 0));
		getContentPane().add(panelBottom, BorderLayout.SOUTH);

		// Find panel
		JPanel panelFind = new JPanel();
		FlowLayout flowLayout_1 = (FlowLayout) panelFind.getLayout();
		flowLayout_1.setVgap(0);
		panelBottom.add(panelFind, BorderLayout.EAST);

		JLabel label = new JLabel(LABELS.getString("lblFilter"));
		panelFind.add(label);

		JTextField textFieldFilter = new JTextField();
		textFieldFilter.setColumns(20);
		textFieldFilter.setBorder(BorderFactory.createEmptyBorder(2, 1, 2, 1));
		panelFind.add(textFieldFilter);
		textFieldFilter.getDocument().addDocumentListener(new DocumentListener() {
			private final int[] cols = new int[] {COL_NAME, COL_IP};
			@Override
			public void changedUpdate(DocumentEvent e) {
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				setRowFilter(textFieldFilter.getText());
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				setRowFilter(textFieldFilter.getText());
			}

			public void setRowFilter(String filter) {
				TableRowSorter<?> sorter = (TableRowSorter<?>)table.getRowSorter();
				if(filter.length() > 0) {
					filter = filter.replace("\\E", "\\e");
					sorter.setRowFilter(RowFilter.regexFilter("(?i).*\\Q" + filter + "\\E.*", cols));
				} else {
					sorter.setRowFilter(null);
				}
			}
		});
		getRootPane().registerKeyboardAction(e -> textFieldFilter.requestFocus(), KeyStroke.getKeyStroke(KeyEvent.VK_F, MainView.SHORTCUT_KEY), JComponent.WHEN_IN_FOCUSED_WINDOW);

		final Action eraseFilterAction = new UsnaAction(this, "/images/erase-9-16.png", null, e -> {
			textFieldFilter.setText("");
			textFieldFilter.requestFocusInWindow();
			table.clearSelection();
		});

		JButton eraseFilterButton = new JButton(eraseFilterAction);
		eraseFilterButton.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		eraseFilterButton.setContentAreaFilled(false);
		eraseFilterButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_E, MainView.SHORTCUT_KEY), "find_erase");
		eraseFilterButton.getActionMap().put("find_erase", eraseFilterAction);
		panelFind.add(eraseFilterButton);

		JPanel panelButtons = new JPanel();
		panelBottom.add(panelButtons, BorderLayout.WEST);

		JButton btnClose = new JButton(LABELS.getString("dlgClose"));
		btnClose.addActionListener(e -> dispose());
		panelButtons.setLayout(new GridLayout(0, 3, 0, 0));
		panelButtons.add(btnClose);

		JButton btnRefresh = new JButton(LABELS.getString("labelRefresh"));
		btnRefresh.addActionListener(e -> {
			tModel.clear();
			exeService.shutdownNow();
			fill();
			try {
				Thread.sleep(250); // too many call disturb some devices at least (2.5)
			} catch (InterruptedException e1) {}
		});
		panelButtons.add(btnRefresh);
		
		browseAction.setEnabled(false);
		JButton btnEdit = new JButton(/*LABELS.getString("edit")*/browseAction);
		panelButtons.add(btnEdit);
		
		table.getSelectionModel().addListSelectionListener(l -> {
			boolean sel = table.getSelectedRow() >= 0;
			browseAction.setEnabled(sel);
			rebootAction.setEnabled(sel);
		});
		
		table.addMouseListener(new MouseAdapter() {
		    public void mousePressed(MouseEvent evt) {
		        if (evt.getClickCount() == 2 && table.getSelectedRow() != -1) {
		        	browseAction.actionPerformed(null);
		        }
		    }
		});

		UsnaPopupMenu tablePopup = new UsnaPopupMenu(ecoModeAction, ledAction, bleAction, apModeAction, rangeExtenderAction, null, browseAction, rebootAction) {
			private static final long serialVersionUID = 1L;
			@Override
			protected void doPopup(MouseEvent evt) {
				final int r = table.rowAtPoint(evt.getPoint());
				if(r >= 0) {
					table.setRowSelectionInterval(r, r);
					ShellyAbstractDevice d = getLocalDevice(table.convertRowIndexToModel(r));
					
					Object eco = tModel.getValueAt(table.convertRowIndexToModel(r), COL_ECO);
					ecoModeAction.setEnabled(eco instanceof Boolean);
					Object led = tModel.getValueAt(table.convertRowIndexToModel(r), COL_LED);
					ledAction.setEnabled(led instanceof Boolean);
					Object ble = tModel.getValueAt(table.convertRowIndexToModel(r), COL_BLE);
					bleAction.setEnabled(ble instanceof Boolean);
					apModeAction.setEnabled(d instanceof AbstractG2Device);
					rangeExtenderAction.setEnabled(d instanceof AbstractG2Device);
					
					show(table, evt.getX(), evt.getY());
				}
			}
		};
		table.addMouseListener(tablePopup.getMouseListener());

		setSize(800, 420);
		setVisible(true);
		setLocationRelativeTo(owner);
		table.columnsWidthAdapt();
		
		appModel.addListener(this);
	}
	
	private Action ecoModeAction = new UsnaAction(this, "setEcoMode_action", e -> {
		int localRow = table.getSelectedModelRow();
		Boolean eco = (Boolean)tModel.getValueAt(localRow, COL_ECO);
		ShellyAbstractDevice d = getLocalDevice(localRow);
		d.setEcoMode(! eco);
		try {TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);} catch (InterruptedException e1) {}
		updateRow(d, localRow);
	});
	
	private Action ledAction = new UsnaAction(this, "setLED_action", e -> {
		int localRow = table.getSelectedModelRow();
		Boolean led = (Boolean)tModel.getValueAt(localRow, COL_LED);
		AbstractG1Device d = (AbstractG1Device)getLocalDevice(localRow);
		d.setLEDMode(! led);
		try {TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);} catch (InterruptedException e1) {}
		updateRow(d, localRow);
	});
	
	private Action bleAction = new UsnaAction(this, "setBLE_action", e -> {
		int localRow = table.getSelectedModelRow();
		Boolean ble = (Boolean)tModel.getValueAt(localRow, COL_BLE);
		AbstractG2Device d = (AbstractG2Device)getLocalDevice(localRow);
		d.setBLEMode(! ble);
		try {TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);} catch (InterruptedException e1) {}
		updateRow(d, localRow);
	});

	private Action apModeAction = new UsnaAction(this, "setAPMode_action", e -> {
		int localRow = table.getSelectedModelRow();
		Object ap = tModel.getValueAt(localRow, COL_AP);
		AbstractG2Device d = (AbstractG2Device)getLocalDevice(localRow);
		WIFIManagerG2.enableAP(d, ! ((ap instanceof Boolean && ap == Boolean.TRUE) || (ap instanceof String && TRUE.equals(ap))));
		try {TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);} catch (InterruptedException e1) {}
		updateRow(d, localRow);
	});
	
	private Action rangeExtenderAction = new UsnaAction(this, "setExtender_action", e -> {
		int localRow = table.getSelectedModelRow();
		Object ext = tModel.getValueAt(localRow, COL_EXTENDER);
		AbstractG2Device d = (AbstractG2Device)getLocalDevice(localRow);
		RangeExtenderManager.enable(d, "-".equals(ext));
		try {TimeUnit.MILLISECONDS.sleep(Devices.MULTI_QUERY_DELAY);} catch (InterruptedException e1) {}
		updateRow(d, localRow);
	});
	
	private Action rebootAction = new UsnaAction(this, "action_reboot_tooltip"/*"Reboot"*/, e -> {
		int localRow = table.getSelectedModelRow();
		tModel.setValueAt(DevicesTable.UPDATING_BULLET, localRow, COL_STATUS);
		appModel.reboot(devicesInd[localRow]);
	});
	
	private Action browseAction = new UsnaAction(this, "edit", e -> {
		try {
			Desktop.getDesktop().browse(new URI("http://" + InetAddressAndPort.toString(getLocalDevice(table.getSelectedModelRow()))));
		} catch (IOException | URISyntaxException ex) {
			Msg.errorMsg(ex);
		}
	});

	@Override
	public void dispose() {
		appModel.removeListener(this);
		exeService.shutdownNow();
		super.dispose();
	}
	
	private void fill() {
		exeService = Executors.newFixedThreadPool(20);
		for (int devicesInd : devicesInd) {
			ShellyAbstractDevice d = appModel.get(devicesInd);
			final int row = tModel.addRow(DevicesTable.UPDATING_BULLET, UtilCollecion.getExtendedHostName(d), new InetAddressAndPort(d));
			updateRow(d, row);
		}
	}
	
	private void updateRow(ShellyAbstractDevice d, int row) {
		exeService.execute(() -> {
			try {
				if(d instanceof AbstractG1Device) {
					tModel.setRow(row, g1Row(d, d.getJSON("/settings")));
				} else { // G2
					tModel.setRow(row, g2Row(d, d.getJSON("/rpc/Shelly.GetConfig"), d.getJSON("/rpc/Shelly.GetStatus")));
				}
			} catch (/*IO*/Exception e) {
				if(d instanceof BatteryDeviceInterface) {
					if(d instanceof AbstractG1Device) {
						tModel.setRow(row, g1Row(d, ((BatteryDeviceInterface)d).getStoredJSON("/settings")));
					} else {
						tModel.setRow(row, g2Row(d, ((BatteryDeviceInterface)d).getStoredJSON("/rpc/Shelly.GetConfig"), null));
					}
				} else {
					tModel.setRow(row, DevicesTable.getStatusIcon(d), UtilCollecion.getExtendedHostName(d), new InetAddressAndPort(d));
				}
				if(d.getStatus() == Status.OFF_LINE || d.getStatus() == Status.NOT_LOOGGED) {
//					LOG.debug("{}", d, e);
				} else {
					LOG.error("{}", d, e);
				}
			}
		});
	}
	
	private static Object[] g1Row(ShellyAbstractDevice d, JsonNode settings) {
		Boolean eco = boolVal(settings.path("eco_mode_enabled"));
		Boolean ledOff = boolVal(settings.path("led_status_disable"));
		boolean debug = d.getDebugMode() != ShellyAbstractDevice.LogMode.NO;
		String roaming;
		if(settings.path("ap_roaming").isMissingNode()) {
			roaming = "-";
		} else if(settings.at("/ap_roaming/enabled").asBoolean()) {
			roaming = settings.at("/ap_roaming/threshold").asText();
		} else {
			roaming = FALSE;
		}
		String wifi1;
		if(settings.at("/wifi_sta/enabled").asBoolean()) {
			wifi1 = "static".equals(settings.at("/wifi_sta/ipv4_method").asText()) ? TRUE : FALSE;
		} else {
			wifi1 = "-";
		}
		String wifi2;
		if(settings.at("/wifi_sta1/enabled").asBoolean()) {
			wifi2 = "static".equals(settings.at("/wifi_sta1/ipv4_method").asText()) ? TRUE : FALSE;
		} else {
			wifi2 = "-";
		}
		return new Object[] {DevicesTable.getStatusIcon(d), UtilCollecion.getExtendedHostName(d), new InetAddressAndPort(d), eco, ledOff, debug, "-", "-", roaming, wifi1, wifi2, "-"};
	}
	
	private static Object[] g2Row(ShellyAbstractDevice d, JsonNode settings, JsonNode status) {
		Boolean eco = boolVal(settings.at("/sys/device/eco_mode"));
		Object ap = boolVal(settings.at("/wifi/ap/enable"));
		if(ap != null && ap == Boolean.TRUE && settings.at("/wifi/ap/is_open").asBoolean(true) == false) {
			ap = TRUE; // AP active but protected with pwd
		}
		Object debug = (d.getDebugMode() == ShellyAbstractDevice.LogMode.NO) ? Boolean.FALSE : LABELS.getString("debug" + d.getDebugMode());
		Boolean ble = boolVal(settings.at("/ble/enable"));
		String roaming;
		if(settings.at("/wifi/roam").isMissingNode()) {
			roaming = "-";
		} else if(settings.at("/wifi/roam/interval").asInt() > 0) {
			roaming = settings.at("/wifi/roam/rssi_thr").asText();
		} else {
			roaming = FALSE;
		}
		String wifi1;
		if(settings.at("/wifi/sta/enable").asBoolean()) {
			wifi1 = "static".equals(settings.at("/wifi/sta/ipv4mode").asText()) ? TRUE : FALSE;
		} else {
			wifi1 = "-";
		}
		String wifi2;
		if(settings.at("/wifi/sta1/enable").asBoolean()) {
			wifi2 = "static".equals(settings.at("/wifi/sta1/ipv4mode").asText()) ? TRUE : FALSE;
		} else {
			wifi2 = "-";
		}
		JsonNode extClient;
		String extender = (status == null || (extClient = status.at("/wifi/ap_client_count")).isMissingNode()) ? "-" : extClient.asInt() + "";
		return new Object[] {DevicesTable.getStatusIcon(d), UtilCollecion.getExtendedHostName(d), new InetAddressAndPort(d), eco, "-", debug, ble, ap, roaming, wifi1, wifi2, extender};
	}

	private static Boolean boolVal(JsonNode node) {
		return node.isMissingNode() ? null : node.asBoolean();
	}

	private static class CheckRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		private final boolean goodVal;

		private CheckRenderer(boolean goodVal) {
			this.goodVal = goodVal;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component ret;
			if(value instanceof Boolean) {
				ret = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
				if((Boolean)value) {
					((JLabel)ret).setText(TRUE);
					if(isSelected == false) {
						ret.setForeground(goodVal ? Color.green : Color.red);
					}
				} else {
					((JLabel)ret).setText(FALSE);
					if(isSelected == false) {
						ret.setForeground(goodVal ? Color.red : Color.green);
					}
				}
			} else {
				ret = super.getTableCellRendererComponent(table, value == null ? "-" : value, isSelected, hasFocus, row, column);
				if(isSelected == false) {
					ret.setForeground(table.getForeground());
				}
			}
			return ret;
		}
	}
	
	private ShellyAbstractDevice getLocalDevice(int ind) {
		return appModel.get(devicesInd[ind]);
	}
	
	private int getLocalIndex(int ind) {
		for(int i = 0; i < devicesInd.length; i++) {
			if(devicesInd[i] == ind) {
				return i;
			}
		}
		return -1;
	}
	
	@Override
	public void update(EventType mesgType, Integer pos) {
		if(mesgType == Devices.EventType.CLEAR) {
			SwingUtilities.invokeLater(() -> dispose()); // devicesInd changes
		} else if(mesgType == Devices.EventType.UPDATE) {
			try {
				final int index = getLocalIndex(pos);
				if(index >= 0 && tModel.getValueAt(index, COL_STATUS) != DevicesTable.UPDATING_BULLET) {
					tModel.setValueAt(DevicesTable.getStatusIcon(appModel.get(pos)), index, COL_STATUS);
				}
			} catch(RuntimeException e) {} // on "refresh" table row could non exists
		}
	}
}