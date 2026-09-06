using System;
using System.IO;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Net;
using System.Net.Sockets;
using System.Net.NetworkInformation;
using System.Threading;
using System.Windows.Forms;
using System.Diagnostics;
using vJoyInterfaceWrap;

namespace TouchRacerReceiver
{
    // Fast GDI+ Bar with Zero Interpolation Lag (0.0 ms Instant Snap)
    public class FastBar : Control
    {
        private float value = 0f;
        private Color barColor = Color.FromArgb(0, 230, 118);
        private Color bgColor = Color.FromArgb(28, 34, 46);
        private bool isCentered = false;

        public FastBar(bool centered = false)
        {
            this.isCentered = centered;
            this.SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        public void SetColor(Color color)
        {
            this.barColor = color;
            this.Invalidate();
        }

        public void SetValue(float val)
        {
            if (Math.Abs(this.value - val) > 0.001f)
            {
                this.value = val;
                this.Invalidate();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.None;

            int w = this.Width;
            int h = this.Height;

            using (Brush bgBrush = new SolidBrush(bgColor))
            {
                g.FillRectangle(bgBrush, 0, 0, w, h);
            }

            using (Brush fillBrush = new SolidBrush(barColor))
            {
                if (isCentered)
                {
                    int cx = w / 2;
                    float clamped = Math.Max(-1f, Math.Min(1f, value));
                    if (clamped > 0)
                    {
                        int barW = (int)(clamped * cx);
                        g.FillRectangle(fillBrush, cx, 0, barW, h);
                    }
                    else if (clamped < 0)
                    {
                        int barW = (int)(-clamped * cx);
                        g.FillRectangle(fillBrush, cx - barW, 0, barW, h);
                    }
                    using (Pen p = new Pen(Color.FromArgb(160, 255, 255, 255), 1.5f))
                    {
                        g.DrawLine(p, cx, 0, cx, h);
                    }
                }
                else
                {
                    float clamped = Math.Max(0f, Math.Min(1f, value));
                    int barW = (int)(clamped * w);
                    if (barW > 0)
                    {
                        g.FillRectangle(fillBrush, 0, 0, barW, h);
                    }
                }
            }

            using (Pen borderPen = new Pen(Color.FromArgb(60, 75, 95), 1f))
            {
                g.DrawRectangle(borderPen, 0, 0, w - 1, h - 1);
            }
        }
    }

    public class ReceiverForm : Form
    {
        private vJoy joystick;
        private uint vJoyId = 1;
        private bool vJoyAcquired = false;

        // Atomic JoystickState struct to update vJoy in a SINGLE kernel IOCTL!
        private vJoy.JoystickState joystickState = new vJoy.JoystickState();

        // Network listeners
        private UdpClient udpServer;
        private TcpListener tcpServer;
        private Thread udpThread;
        private Thread tcpThread;
        private volatile bool isRunning = true;

        // UI Controls
        private Label lblTitle;
        private Label lblIpAddress;
        private Label lblVJoyStatus;
        private Label lblNetworkStatus;
        private Label lblPacketRate;

        private FastBar barSteering;
        private Label lblSteeringVal;

        private FastBar barThrottle;
        private Label lblThrottleVal;

        private FastBar barBrake;
        private Label lblBrakeVal;

        private RadioButton rbSplitPedals;
        private RadioButton rbCombinedPedals;

        private Button btnReleaseVJoy;

        private Panel[] pnlButtons = new Panel[8];
        private Label[] lblButtons = new Label[8];

        private System.Windows.Forms.Timer uiTimer;

        // Telemetry State
        private int currentSteer = 16384;
        private int currentGas = 0;
        private int currentBrake = 0;
        private int currentButtonMask = 0;

        // Shifter pulse latch timers
        private long shiftUpUntil = 0;
        private long shiftDownUntil = 0;

        private int packetCount = 0;
        private int currentHz = 0;
        private string clientEndpoint = "";

        public ReceiverForm()
        {
            // Boost process priority so Windows never delays this app when games run in fullscreen!
            try
            {
                Process.GetCurrentProcess().PriorityClass = ProcessPriorityClass.High;
            }
            catch { }

            InitializeComponent();
            InitVJoy();
            StartNetworkListeners();

            uiTimer = new System.Windows.Forms.Timer();
            uiTimer.Interval = 25; // 40 FPS UI refresh when visible
            uiTimer.Tick += UiTimer_Tick;
            uiTimer.Start();

            // Background power saving: Pause UI timer when minimized
            this.Resize += (s, e) =>
            {
                if (this.WindowState == FormWindowState.Minimized)
                {
                    uiTimer.Stop();
                }
                else
                {
                    uiTimer.Start();
                }
            };
        }

        private void InitializeComponent()
        {
            this.Text = "HeelAndThumb Rx v0.1.1-beta - PC Receiver for Android Steering Wheel";
            string icoPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app.ico");
            if (File.Exists(icoPath))
            {
                try { this.Icon = new Icon(icoPath); } catch { }
            }
            this.Size = new Size(520, 650);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.FixedSingle;
            this.MaximizeBox = true;
            this.BackColor = Color.FromArgb(18, 22, 28);
            this.ForeColor = Color.White;
            this.Font = new Font("Segoe UI", 9F);

            // Title
            lblTitle = new Label();
            lblTitle.Text = "HEEL & THUMB RX v0.1.1 Beta";
            lblTitle.Size = new Size(350, 25);
            lblTitle.Font = new Font("Segoe UI", 13F, FontStyle.Bold);
            lblTitle.ForeColor = Color.FromArgb(0, 229, 255);
            lblTitle.Location = new Point(16, 12);
            lblTitle.Size = new Size(300, 25);
            this.Controls.Add(lblTitle);

            // Local IP Address
            lblIpAddress = new Label();
            lblIpAddress.Text = "PC IP: " + GetLocalIpAddresses();
            lblIpAddress.Font = new Font("Segoe UI", 9.5F, FontStyle.Bold);
            lblIpAddress.ForeColor = Color.FromArgb(255, 215, 0);
            lblIpAddress.Location = new Point(16, 40);
            lblIpAddress.Size = new Size(470, 20);
            this.Controls.Add(lblIpAddress);

            // vJoy Status
            lblVJoyStatus = new Label();
            lblVJoyStatus.Text = "vJoy: Initializing...";
            lblVJoyStatus.Location = new Point(16, 65);
            lblVJoyStatus.Size = new Size(320, 20);
            this.Controls.Add(lblVJoyStatus);

            btnReleaseVJoy = new Button();
            btnReleaseVJoy.Text = "Take vJoy Control";
            btnReleaseVJoy.Location = new Point(340, 62);
            btnReleaseVJoy.Size = new Size(145, 26);
            btnReleaseVJoy.BackColor = Color.FromArgb(35, 45, 60);
            btnReleaseVJoy.FlatStyle = FlatStyle.Flat;
            btnReleaseVJoy.Click += BtnReleaseVJoy_Click;
            this.Controls.Add(btnReleaseVJoy);

            // Network status & Hz
            lblNetworkStatus = new Label();
            lblNetworkStatus.Text = "Network: High-Speed Listener (Port 41503)";
            lblNetworkStatus.ForeColor = Color.FromArgb(160, 180, 200);
            lblNetworkStatus.Location = new Point(16, 92);
            lblNetworkStatus.Size = new Size(370, 20);
            this.Controls.Add(lblNetworkStatus);

            lblPacketRate = new Label();
            lblPacketRate.Text = "0 Hz";
            lblPacketRate.Font = new Font("Segoe UI", 10F, FontStyle.Bold);
            lblPacketRate.ForeColor = Color.FromArgb(0, 230, 118);
            lblPacketRate.Location = new Point(410, 90);
            lblPacketRate.Size = new Size(75, 22);
            lblPacketRate.TextAlign = ContentAlignment.MiddleRight;
            this.Controls.Add(lblPacketRate);

            // Divider
            Panel div1 = new Panel();
            div1.Location = new Point(16, 118);
            div1.Size = new Size(470, 1);
            div1.BackColor = Color.FromArgb(50, 60, 75);
            this.Controls.Add(div1);

            // --- INSTANT GAUGES (0ms delay, no Windows ProgressBar animation lag) ---
            int y = 128;

            // Steering (Percentage based)
            Label lblSteerTitle = new Label();
            lblSteerTitle.Text = "Steering (Axis X):";
            lblSteerTitle.Location = new Point(16, y);
            lblSteerTitle.Size = new Size(150, 18);
            this.Controls.Add(lblSteerTitle);

            lblSteeringVal = new Label();
            lblSteeringVal.Text = "0% (Center)";
            lblSteeringVal.TextAlign = ContentAlignment.MiddleRight;
            lblSteeringVal.Location = new Point(330, y);
            lblSteeringVal.Size = new Size(155, 18);
            lblSteeringVal.ForeColor = Color.FromArgb(0, 229, 255);
            this.Controls.Add(lblSteeringVal);

            barSteering = new FastBar(true);
            barSteering.SetColor(Color.FromArgb(0, 229, 255));
            barSteering.Location = new Point(16, y + 20);
            barSteering.Size = new Size(470, 22);
            this.Controls.Add(barSteering);

            // Throttle (Gas)
            y += 52;
            Label lblGasTitle = new Label();
            lblGasTitle.Text = "Throttle / Gas (Axis Y):";
            lblGasTitle.Location = new Point(16, y);
            lblGasTitle.Size = new Size(150, 18);
            this.Controls.Add(lblGasTitle);

            lblThrottleVal = new Label();
            lblThrottleVal.Text = "0%";
            lblThrottleVal.TextAlign = ContentAlignment.MiddleRight;
            lblThrottleVal.Location = new Point(330, y);
            lblThrottleVal.Size = new Size(155, 18);
            lblThrottleVal.ForeColor = Color.FromArgb(0, 230, 118);
            this.Controls.Add(lblThrottleVal);

            barThrottle = new FastBar(false);
            barThrottle.SetColor(Color.FromArgb(0, 230, 118));
            barThrottle.Location = new Point(16, y + 20);
            barThrottle.Size = new Size(470, 22);
            this.Controls.Add(barThrottle);

            // Brake
            y += 52;
            Label lblBrakeTitle = new Label();
            lblBrakeTitle.Text = "Brake Pedal (Axis Z / Split):";
            lblBrakeTitle.Location = new Point(16, y);
            lblBrakeTitle.Size = new Size(170, 18);
            this.Controls.Add(lblBrakeTitle);

            lblBrakeVal = new Label();
            lblBrakeVal.Text = "0%";
            lblBrakeVal.TextAlign = ContentAlignment.MiddleRight;
            lblBrakeVal.Location = new Point(330, y);
            lblBrakeVal.Size = new Size(155, 18);
            lblBrakeVal.ForeColor = Color.FromArgb(255, 23, 68);
            this.Controls.Add(lblBrakeVal);

            barBrake = new FastBar(false);
            barBrake.SetColor(Color.FromArgb(255, 23, 68));
            barBrake.Location = new Point(16, y + 20);
            barBrake.Size = new Size(470, 22);
            this.Controls.Add(barBrake);

            // --- PEDAL MODES ---
            y += 54;
            rbSplitPedals = new RadioButton();
            rbSplitPedals.Text = "Split Pedals (Sim: Gas = Axis Y, Brake = Axis Z - 100% simultaneous!)";
            rbSplitPedals.Location = new Point(18, y);
            rbSplitPedals.Size = new Size(460, 22);
            rbSplitPedals.Checked = true;
            rbSplitPedals.ForeColor = Color.FromArgb(255, 215, 0);
            this.Controls.Add(rbSplitPedals);

            rbCombinedPedals = new RadioButton();
            rbCombinedPedals.Text = "Combined Pedals (Arcade Legacy: Single Axis Y)";
            rbCombinedPedals.Location = new Point(18, y + 24);
            rbCombinedPedals.Size = new Size(460, 22);
            rbCombinedPedals.ForeColor = Color.FromArgb(170, 185, 205);
            this.Controls.Add(rbCombinedPedals);

            // Divider
            Panel div2 = new Panel();
            div2.Location = new Point(16, y + 54);
            div2.Size = new Size(470, 1);
            div2.BackColor = Color.FromArgb(50, 60, 75);
            this.Controls.Add(div2);

            // --- BUTTONS PANEL ---
            y += 64;
            Label lblBtnHeader = new Label();
            lblBtnHeader.Text = "vJoy Buttons (Handbrake is Dedicated Button 3):";
            lblBtnHeader.Location = new Point(16, y);
            lblBtnHeader.Size = new Size(360, 18);
            this.Controls.Add(lblBtnHeader);

            string[] buttonLabels = new string[] {
                "1: Shift Up (RB)",
                "2: Shift Down (LB)",
                "3: Handbrake (Dedicated)",
                "4: Boost (NOS)",
                "5: Reset",
                "6: Camera",
                "7: Pause",
                "8: Horn"
            };

            int btnY = y + 22;
            for (int i = 0; i < 8; i++)
            {
                int col = i % 2;
                int row = i / 2;
                int bx = 16 + col * 240;
                int by = btnY + row * 38;

                Panel pnl = new Panel();
                pnl.Location = new Point(bx, by);
                pnl.Size = new Size(230, 30);
                pnl.BackColor = Color.FromArgb(28, 34, 46);
                pnl.BorderStyle = BorderStyle.FixedSingle;

                Label lbl = new Label();
                lbl.Text = buttonLabels[i];
                lbl.Dock = DockStyle.Fill;
                lbl.TextAlign = ContentAlignment.MiddleCenter;
                lbl.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
                lbl.ForeColor = Color.FromArgb(170, 180, 195);
                pnl.Controls.Add(lbl);

                pnlButtons[i] = pnl;
                lblButtons[i] = lbl;
                this.Controls.Add(pnl);
            }
        }

        private void InitVJoy()
        {
            try
            {
                joystick = new vJoy();
                if (!joystick.vJoyEnabled())
                {
                    lblVJoyStatus.Text = "vJoy: Driver not enabled or missing!";
                    lblVJoyStatus.ForeColor = Color.Red;
                    return;
                }

                VjdStat status = joystick.GetVJDStatus(vJoyId);
                if (status == VjdStat.VJD_STAT_FREE || status == VjdStat.VJD_STAT_OWN)
                {
                    if (joystick.AcquireVJD(vJoyId))
                    {
                        vJoyAcquired = true;
                        lblVJoyStatus.Text = "vJoy: Device 1 ACQUIRED (Ready!)";
                        lblVJoyStatus.ForeColor = Color.FromArgb(0, 230, 118);
                        btnReleaseVJoy.Text = "Release vJoy";
                        joystick.ResetAll();

                        // Initialize struct
                        joystickState.bDevice = (byte)vJoyId;
                        joystickState.AxisX = 16384;
                        joystickState.AxisY = 0;
                        joystickState.AxisZ = 0;
                        joystickState.Buttons = 0;
                        joystick.UpdateVJD(vJoyId, ref joystickState);
                    }
                }
                else if (status == VjdStat.VJD_STAT_BUSY)
                {
                    int ownerPid = joystick.GetOwnerPid(vJoyId);
                    string procName = "Unknown";
                    try { procName = Process.GetProcessById(ownerPid).ProcessName; } catch { }
                    lblVJoyStatus.Text = string.Format("vJoy: BUSY (Owned by {0}, PID {1})", procName, ownerPid);
                    lblVJoyStatus.ForeColor = Color.FromArgb(255, 180, 40);
                    btnReleaseVJoy.Text = "Close " + procName;
                }
            }
            catch (Exception ex)
            {
                lblVJoyStatus.Text = "vJoy error: " + ex.Message;
                lblVJoyStatus.ForeColor = Color.Red;
            }
        }

        private void BtnReleaseVJoy_Click(object sender, EventArgs e)
        {
            if (vJoyAcquired)
            {
                joystick.RelinquishVJD(vJoyId);
                vJoyAcquired = false;
                lblVJoyStatus.Text = "vJoy: Device 1 Relinquished";
                lblVJoyStatus.ForeColor = Color.Gray;
                btnReleaseVJoy.Text = "Acquire vJoy";
            }
            else
            {
                foreach (Process p in Process.GetProcessesByName("Touch Racer"))
                {
                    try { p.Kill(); p.WaitForExit(1000); } catch { }
                }
                Thread.Sleep(200);
                InitVJoy();
            }
        }

        private void StartNetworkListeners()
        {
            // UDP Listener on 41503 with High Priority Thread
            udpThread = new Thread(() =>
            {
                try
                {
                    udpServer = new UdpClient(41503);
                    udpServer.Client.ReceiveBufferSize = 1048576; // 1MB buffer
                    IPEndPoint remoteEP = new IPEndPoint(IPAddress.Any, 0);

                    while (isRunning)
                    {
                        byte[] bytes = udpServer.Receive(ref remoteEP);
                        if (bytes != null && bytes.Length >= 8)
                        {
                            clientEndpoint = remoteEP.ToString();
                            ProcessPacket(bytes);
                        }
                    }
                }
                catch (Exception) { }
            });
            udpThread.IsBackground = true;
            udpThread.Priority = ThreadPriority.Highest; // Highest priority for instant responsiveness!
            udpThread.Start();

            // TCP Listener on 41503 with High Priority Thread
            tcpThread = new Thread(() =>
            {
                try
                {
                    tcpServer = new TcpListener(IPAddress.Any, 41503);
                    tcpServer.Start();

                    while (isRunning)
                    {
                        TcpClient client = tcpServer.AcceptTcpClient();
                        client.NoDelay = true; // Disable Nagle's algorithm for sub-millisecond instant packets!
                        client.ReceiveBufferSize = 65536;

                        Thread clientThread = new Thread(new ParameterizedThreadStart(HandleTcpClient));
                        clientThread.IsBackground = true;
                        clientThread.Priority = ThreadPriority.Highest;
                        clientThread.Start(client);
                    }
                }
                catch (Exception) { }
            });
            tcpThread.IsBackground = true;
            tcpThread.Priority = ThreadPriority.Highest;
            tcpThread.Start();
        }

        private void HandleTcpClient(object state)
        {
            using (TcpClient client = (TcpClient)state)
            {
                clientEndpoint = client.Client.RemoteEndPoint.ToString();
                NetworkStream stream = client.GetStream();
                byte[] buffer = new byte[256];

                while (isRunning && client.Connected)
                {
                    int bytesRead = stream.Read(buffer, 0, buffer.Length);
                    if (bytesRead <= 0) break;

                    for (int offset = 0; offset <= bytesRead - 8; offset += 8)
                    {
                        byte[] packet = new byte[8];
                        Array.Copy(buffer, offset, packet, 0, 8);
                        ProcessPacket(packet);
                    }
                }
            }
        }

        private void ProcessPacket(byte[] data)
        {
            if (data == null || data.Length < 8 || data[0] != 0x01) return;

            Interlocked.Increment(ref packetCount);

            // Fast Big-Endian extraction
            int steer = (data[1] << 8) | data[2];
            int gas = (data[3] << 8) | data[4];
            int brake = (data[5] << 8) | data[6];
            int buttons = data[7];

            currentSteer = steer;
            currentGas = gas;
            currentBrake = brake;
            currentButtonMask = buttons;

            // Shifter Latch Buffer in Receiver:
            // When Shift Up is received, hold vJoy Button 1 for 120ms
            long nowMs = Environment.TickCount;
            if ((buttons & (1 << 0)) != 0)
            {
                shiftUpUntil = nowMs + 120;
            }
            if ((buttons & (1 << 1)) != 0)
            {
                shiftDownUntil = nowMs + 120;
            }

            // ATOMIC SINGLE-IOCTL vJoy Driver Update (Zero Kernel Bottleneck!)
            if (vJoyAcquired && joystick != null)
            {
                joystickState.bDevice = (byte)vJoyId;
                joystickState.AxisX = steer;

                if (rbSplitPedals.Checked)
                {
                    // Split Pedals: Gas = Axis Y, Brake = Axis Z!
                    joystickState.AxisY = gas;
                    joystickState.AxisZ = brake;
                }
                else
                {
                    // Combined Pedal -> Axis Y: 16384 + (gas - brake) / 2
                    int combined = 16384 + (gas / 2) - (brake / 2);
                    joystickState.AxisY = Math.Max(0, Math.Min(32767, combined));
                }

                // Compute button bitmask
                uint finalButtons = 0;

                // Shift Up (Button 1) with 120ms latch
                if (nowMs < shiftUpUntil) finalButtons |= (1 << 0);
                // Shift Down (Button 2) with 120ms latch
                if (nowMs < shiftDownUntil) finalButtons |= (1 << 1);

                // Handbrake (Button 3) - 100% PURE DEDICATED BUTTON
                if ((buttons & (1 << 2)) != 0) finalButtons |= (1 << 2);

                // Buttons 4 to 8
                for (int b = 3; b < 8; b++)
                {
                    if ((buttons & (1 << b)) != 0) finalButtons |= (1u << b);
                }

                joystickState.Buttons = finalButtons;

                // Single atomic driver call!
                joystick.UpdateVJD(vJoyId, ref joystickState);
            }
        }

        private long lastSec = DateTime.UtcNow.Ticks;

        private void UiTimer_Tick(object sender, EventArgs e)
        {
            // If minimized, skip UI drawing completely to save CPU!
            if (this.WindowState == FormWindowState.Minimized || !this.Visible)
            {
                return;
            }

            // Calculate Hz
            long now = DateTime.UtcNow.Ticks;
            if (now - lastSec >= 10000000)
            {
                currentHz = packetCount;
                packetCount = 0;
                lastSec = now;

                lblPacketRate.Text = currentHz + " Hz";
                if (!string.IsNullOrEmpty(clientEndpoint))
                {
                    lblNetworkStatus.Text = "Connected: " + clientEndpoint;
                    lblNetworkStatus.ForeColor = Color.FromArgb(0, 230, 118);
                }
            }

            // Update Percentage-based Steering Display (-100% to +100%)
            float normSteer = (currentSteer - 16384) / 16384.0f;
            barSteering.SetValue(normSteer);

            float steerPct = normSteer * 100.0f;
            if (Math.Abs(steerPct) < 0.5f)
            {
                lblSteeringVal.Text = "0% (Center)";
            }
            else if (steerPct < 0)
            {
                lblSteeringVal.Text = string.Format("L {0:0}%", -steerPct);
            }
            else
            {
                lblSteeringVal.Text = string.Format("R {0:0}%", steerPct);
            }

            // Instant GDI+ FastBars (0.0 ms delay)
            float gasRatio = currentGas / 32767.0f;
            barThrottle.SetValue(gasRatio);
            lblThrottleVal.Text = (int)(gasRatio * 100f) + "%";

            float brakeRatio = currentBrake / 32767.0f;
            barBrake.SetValue(brakeRatio);
            lblBrakeVal.Text = (int)(brakeRatio * 100f) + "%";

            // Update Button Highlights
            long nowMs = Environment.TickCount;
            bool shiftUpActive = (nowMs < shiftUpUntil);
            bool shiftDownActive = (nowMs < shiftDownUntil);

            for (int i = 0; i < 8; i++)
            {
                bool active = false;
                if (i == 0) active = shiftUpActive;
                else if (i == 1) active = shiftDownActive;
                else active = (currentButtonMask & (1 << i)) != 0;

                if (active)
                {
                    pnlButtons[i].BackColor = (i == 2) ? Color.FromArgb(220, 23, 68) : // Handbrake Red
                                              (i == 0 || i == 1) ? Color.FromArgb(0, 200, 83) : // Shifters Green
                                              Color.FromArgb(0, 229, 255); // Others Cyan
                    lblButtons[i].ForeColor = Color.Black;
                }
                else
                {
                    pnlButtons[i].BackColor = Color.FromArgb(28, 34, 46);
                    lblButtons[i].ForeColor = Color.FromArgb(170, 180, 195);
                }
            }
        }

        private string GetLocalIpAddresses()
        {
            List<string> ips = new List<string>();
            try
            {
                foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus == OperationalStatus.Up &&
                        ni.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                    {
                        foreach (UnicastIPAddressInformation uip in ni.GetIPProperties().UnicastAddresses)
                        {
                            if (uip.Address.AddressFamily == AddressFamily.InterNetwork)
                            {
                                string s = uip.Address.ToString();
                                if (!s.StartsWith("169.254")) ips.Add(s);
                            }
                        }
                    }
                }
            }
            catch { }
            return ips.Count > 0 ? string.Join(" | ", ips) : "127.0.0.1";
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            isRunning = false;
            try { if (udpServer != null) udpServer.Close(); } catch { }
            try { if (tcpServer != null) tcpServer.Stop(); } catch { }
            if (vJoyAcquired && joystick != null)
            {
                joystick.ResetAll();
                joystick.RelinquishVJD(vJoyId);
            }
            base.OnFormClosing(e);
        }

        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new ReceiverForm());
        }
    }
}
