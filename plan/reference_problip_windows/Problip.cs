using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Media;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using Microsoft.Win32;

// PROBLIP — persistent blip (meditation beeper).
// Single self-contained exe, no external dependencies.
// UI: saipen UI.md Golden Default. Text is NON-antialiased (pixel text).
// v3 C# rewrite. No TaskManager/ProcessExplorer — just a configurable beeper.

namespace Problip
{
    static class Native
    {
        [DllImport("gdi32.dll")] public static extern IntPtr CreateFont(int nHeight, int nWidth, int nEscapement, int nOrientation,
            int fnWeight, uint fdwItalic, uint fdwUnderline, uint fdwStrikeOut, uint fdwCharSet,
            uint fdwOutputPrecision, uint fdwClipPrecision, uint fdwQuality, uint fdwPitchAndFamily, string lpszFace);
        [DllImport("gdi32.dll")] public static extern bool DeleteObject(IntPtr handle);

        public const int WM_NCHITTEST = 0x84;
        public const int HTCAPTION = 2;
        public const int NONANTIALIASED_QUALITY = 3;
    }

    static class Palette
    {
        // saipen UI.md Golden Default (RGB)
        public static readonly Color BG        = C(0x1A1810);
        public static readonly Color BG_SOFT   = C(0x232018);
        public static readonly Color SURFACE   = C(0x332E22);
        public static readonly Color RAISED    = C(0x3D372A);
        public static readonly Color ALT       = C(0x453D30);
        public static readonly Color BDARK     = C(0x100E08);
        public static readonly Color BHL       = C(0xF0D060);
        public static readonly Color BEVEL     = C(0x75663D);
        public static readonly Color BMUTED    = C(0x5A5040);
        public static readonly Color TEXT      = C(0xD4C89A);
        public static readonly Color TEXT2     = C(0x9C9371);
        public static readonly Color MUTED     = C(0x6E674E);
        public static readonly Color TEAL      = C(0x008080);
        public static readonly Color TEAL_DEEP = C(0x004C4C);
        public static readonly Color SUCCESS   = C(0x4A7A20);
        public static readonly Color WARNING   = C(0x7A7A20);
        public static readonly Color DANGER    = C(0x7A2020);
        public static readonly Color DANGERTXT = C(0xD66464);
        public static readonly Color SELECTION = C(0x3D372A);
        public static readonly Color COMPARE   = C(0x14120C);
        public static readonly Color LINK      = C(0xF0D060);
        static Color C(int rgb)
        {
            return Color.FromArgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
    }

    // The tray and window icon both come from problip.ico, whose frames are the
    // SAIPEN_OrangeShine avatar downscaled with NEAREST resampling (see
    // Scripts/make_pixel_ico.py) - strictly aliased, no antialiasing. Loading
    // the exact-size frame lets the shell show native pixels instead of
    // resampling a smooth image, so there is no blur at any DPI.
    static class AppIcon
    {
        public static Icon For(string path, int size)
        {
            try
            {
                if (path != null && File.Exists(path))
                    return new Icon(path, size, size);
            }
            catch { }
            using (Icon fallback = SystemIcons.Application) return (Icon)fallback.Clone();
        }
    }

    class Settings
    {
        public string Dir;
        public string IniPath;
        public string WavPath;
        public string IcoPath;
        public double Volume = 0.05;
        public int MinMs = 4000;
        public int MaxMs = 7000;
        public bool AutoStart = true;

        public Settings(string dir)
        {
            Dir = dir;
            IniPath = Path.Combine(dir, "problip.ini");
            WavPath = Path.Combine(dir, "blip01.wav");
            IcoPath = Path.Combine(dir, "problip.ico");
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        static extern int GetPrivateProfileString(string app, string key, string def, System.Text.StringBuilder buf, int size, string file);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        static extern bool WritePrivateProfileString(string app, string key, string val, string file);

        string Read(string key, string def)
        {
            var sb = new System.Text.StringBuilder(260);
            GetPrivateProfileString("problip", key, def, sb, sb.Capacity, IniPath);
            return sb.ToString();
        }
        void Write(string key, string val)
        {
            WritePrivateProfileString("problip", key, val, IniPath);
        }

        public void Load()
        {
            string v;
            v = Read("Volume", "0.05");
            double.TryParse(v, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out Volume);
            if (Volume < 0 || Volume > 1) Volume = 0.05;
            v = Read("MinMs", "4000");
            int.TryParse(v, out MinMs);
            v = Read("MaxMs", "7000");
            int.TryParse(v, out MaxMs);
            if (MaxMs < MinMs) MaxMs = MinMs;
            if (MinMs < 1000) MinMs = 1000;
            if (MaxMs > 60000) MaxMs = 60000;
            AutoStart = Read("AutoStart", "1") == "1";
            if (!File.Exists(IniPath))
            {
                Save("Volume", Volume.ToString("0.00", System.Globalization.CultureInfo.InvariantCulture));
                Save("MinMs", MinMs.ToString());
                Save("MaxMs", MaxMs.ToString());
                Save("AutoStart", "1");
            }
        }

        public void Save(string key, string val)
        {
            Write(key, val);
        }
    }

    // Blip engine: plays a volume-scaled WAV on a jittered interval.
    class BlipEngine
    {
        Settings S;
        SoundPlayer Player;
        string CachePath;
        Random Rng = new Random();
        System.Windows.Forms.Timer Timer;
        long LastPlayMs = -100000;
        System.Diagnostics.Stopwatch Clock = System.Diagnostics.Stopwatch.StartNew();
        public bool Enabled = false;
        public int MinMs, MaxMs;
        // Why the sound could not be loaded, or null when it is playable. A
        // failed load used to collapse to `Player = null` with nothing recorded,
        // while Start() still reported the engine as running -- so a missing or
        // corrupt blip01.wav was an indistinguishable silent no-op.
        public string LoadError;

        public BlipEngine(Settings s)
        {
            S = s;
            MinMs = s.MinMs;
            MaxMs = s.MaxMs;
            Timer = new System.Windows.Forms.Timer();
            Timer.Interval = 500;
            Timer.Tick += Tick;
            BuildCache();
        }

        void BuildCache()
        {
            string previous = CachePath;
            try
            {
                byte[] src = File.ReadAllBytes(S.WavPath);
                // SoundPlayer.Load() accepts arbitrary bytes -- it only reads the
                // stream -- and the format is not rejected until winmm tries to
                // play it, inside a per-tick catch. So the asset is validated
                // here, where the failure can still be reported.
                RequireWav(src);
                byte[] scaled = ScaleWav(src, S.Volume);
                CachePath = Path.Combine(Path.GetTempPath(), "problip_" + Guid.NewGuid().ToString("N") + ".wav");
                File.WriteAllBytes(CachePath, scaled);
                if (Player != null) Player.Dispose();
                Player = new SoundPlayer(CachePath);
                Player.Load();
                LoadError = null;
            }
            catch (Exception ex)
            {
                if (Player != null) { try { Player.Dispose(); } catch { } }
                Player = null;
                LoadError = ex.Message;
            }
            // Every volume change rebuilds the cache. Without this the old temp
            // WAV stays in %TEMP% for good, one file per drag, forever.
            if (previous != null && previous != CachePath)
            {
                try { if (File.Exists(previous)) File.Delete(previous); } catch { }
            }
        }

        // Throws with a readable reason unless the bytes really are a RIFF/WAVE
        // file carrying both a fmt and a data chunk. Same chunk walk ScaleWav
        // uses, so a file this accepts is a file ScaleWav can scale.
        static void RequireWav(byte[] b)
        {
            if (b.Length < 44
                || System.Text.Encoding.ASCII.GetString(b, 0, 4) != "RIFF"
                || System.Text.Encoding.ASCII.GetString(b, 8, 4) != "WAVE")
                throw new InvalidDataException("not a RIFF/WAVE file");
            bool fmt = false, data = false;
            int pos = 12;
            while (pos + 8 <= b.Length)
            {
                string id = System.Text.Encoding.ASCII.GetString(b, pos, 4);
                int len = BitConverter.ToInt32(b, pos + 4);
                if (len < 0 || pos + 8 + len > b.Length) break;
                if (id == "fmt " && len >= 16) fmt = true;
                else if (id == "data" && len > 0) data = true;
                if (fmt && data) return;
                pos += 8 + len + (len % 2);
            }
            throw new InvalidDataException(fmt ? "WAVE file has no audio data" : "WAVE file has no format chunk");
        }

        byte[] ScaleWav(byte[] b, double gain)
        {
            if (gain >= 0.999999) return b;
            byte[] outb = (byte[])b.Clone();
            int dataStart = -1, dataLen = 0, fmtBits = 0, fmtPos = 12;
            while (fmtPos + 8 <= b.Length)
            {
                string id = System.Text.Encoding.ASCII.GetString(b, fmtPos, 4);
                int len = BitConverter.ToInt32(b, fmtPos + 4);
                if (len < 0 || fmtPos + 8 + len > b.Length) break;
                if (id == "fmt " && len >= 16) fmtBits = BitConverter.ToUInt16(b, fmtPos + 22);
                else if (id == "data") { dataStart = fmtPos + 8; dataLen = len; break; }
                fmtPos += 8 + len + (len % 2);
            }
            if (dataStart < 0) return b;
            int bps = fmtBits / 8;
            int end = dataStart + dataLen;
            for (int i = dataStart; i + bps <= end; i += bps)
            {
                if (fmtBits == 8)
                {
                    int v = (int)Math.Round((outb[i] - 128) * gain) + 128;
                    outb[i] = (byte)Math.Max(0, Math.Min(255, v));
                }
                else if (fmtBits == 16)
                {
                    short v = BitConverter.ToInt16(outb, i);
                    int n = (int)Math.Round(v * gain);
                    if (n > 32767) n = 32767; else if (n < -32768) n = -32768;
                    outb[i] = (byte)(n & 0xFF); outb[i + 1] = (byte)((n >> 8) & 0xFF);
                }
                else if (fmtBits == 24)
                {
                    int raw = outb[i] | (outb[i + 1] << 8) | (outb[i + 2] << 16);
                    int v = (raw & 0x800000) != 0 ? raw - 0x1000000 : raw;
                    int n = (int)Math.Round(v * gain);
                    if (n > 8388607) n = 8388607; else if (n < -8388608) n = -8388608;
                    n &= 0xFFFFFF;
                    outb[i] = (byte)(n & 0xFF); outb[i + 1] = (byte)((n >> 8) & 0xFF); outb[i + 2] = (byte)((n >> 16) & 0xFF);
                }
                else if (fmtBits == 32)
                {
                    int v = BitConverter.ToInt32(outb, i);
                    long n = (long)Math.Round((double)v * gain);
                    if (n > int.MaxValue) n = int.MaxValue; else if (n < int.MinValue) n = int.MinValue;
                    byte[] tmp = BitConverter.GetBytes((int)n);
                    outb[i] = tmp[0]; outb[i + 1] = tmp[1]; outb[i + 2] = tmp[2]; outb[i + 3] = tmp[3];
                }
            }
            return outb;
        }

        int NextDelay()
        {
            if (MinMs >= MaxMs) return MinMs;
            return Rng.Next(MinMs, MaxMs + 1);
        }

        void Tick(object sender, EventArgs e)
        {
            if (!Enabled || Player == null) return;
            long now = Clock.ElapsedMilliseconds;
            if (now - LastPlayMs >= 300)
            {
                try { Player.Play(); } catch { }
                LastPlayMs = now;
            }
            Timer.Interval = NextDelay();
        }

        public void Start()
        {
            // A missing or corrupt sound asset is not "running": leaving Enabled
            // true here is exactly what made a broken WAV look identical to a
            // working one. Retry the load once -- the file may have been fixed
            // since startup -- and stay off if it is still unusable.
            if (Player == null)
            {
                BuildCache();
                if (Player == null) { Enabled = false; return; }
            }
            Enabled = true;
            Timer.Interval = 500;
            Timer.Start();
        }
        public void Stop()
        {
            Enabled = false;
            Timer.Stop();
        }
        // Rebuild the cached WAV without changing on/off. Reload used to end in
        // Start() unconditionally, so dragging the volume while the beeper was
        // OFF switched it back on.
        public void Reload()
        {
            bool wasOn = Enabled;
            Stop();
            BuildCache();
            if (wasOn) Start();
        }
        public bool IsOn { get { return Enabled; } }
        public bool IsBroken { get { return Player == null; } }
        // One line the UI can show verbatim: the asset that failed plus why.
        public string FailureText
        {
            get
            {
                if (Player != null) return null;
                return "Sound not loaded: " + Path.GetFileName(S.WavPath)
                    + (string.IsNullOrEmpty(LoadError) ? "" : " — " + LoadError);
            }
        }
        // NotifyIcon.Text rejects anything over 63 characters, so the tray names
        // the failure and the window carries the detail.
        public string TrayCaption
        {
            get
            {
                string t = Player == null ? "problip — sound not loaded" : "problip";
                return t.Length > 63 ? t.Substring(0, 63) : t;
            }
        }

        // Called on the way out: drop the last temp WAV so a normal exit leaves
        // nothing behind in %TEMP%.
        public void Cleanup()
        {
            Stop();
            try { if (Player != null) Player.Dispose(); } catch { }
            Player = null;
            if (CachePath != null)
            {
                try { if (File.Exists(CachePath)) File.Delete(CachePath); } catch { }
                CachePath = null;
            }
        }
    }

    class ProblipForm : Form
    {
        Settings S;
        BlipEngine Engine;
        NotifyIcon Tray;
        List<HotZone> Hot = new List<HotZone>();
        Rectangle VolTrack;
        bool VolDragging = false;

        class HotZone
        {
            public Rectangle R;
            public Action A;
        }

        public ProblipForm(Settings s, BlipEngine engine, NotifyIcon tray)
        {
            S = s; Engine = engine; Tray = tray;
            Text = "problip";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(280, 150);
            BackColor = Palette.BG;
            DoubleBuffered = true;
            TopMost = true;
            // taskbar / alt-tab icon = the same pixel avatar mark
            try { Icon = AppIcon.For(s.IcoPath, SystemInformation.IconSize.Width); }
            catch { }
        }

        protected override void WndProc(ref Message m)
        {
            base.WndProc(ref m);
            if (m.Msg == Native.WM_NCHITTEST)
            {
                // LParam packs two SIGNED 16-bit screen coords. A checked (int)
                // cast of the IntPtr overflows for a point on a monitor above the
                // primary, killing the drag strip there.
                int raw = unchecked((int)m.LParam.ToInt64());
                int x = raw & 0xFFFF;
                int y = (raw >> 16) & 0xFFFF;
                if (x > 0x7FFF) x -= 0x10000;
                if (y > 0x7FFF) y -= 0x10000;
                Point p = PointToClient(new Point(x, y));
                if (p.Y < 20 && p.X < Width - 20)
                {
                    m.Result = (IntPtr)Native.HTCAPTION;
                }
            }
        }

        // Font.FromHfont does NOT take ownership of the handle: every call leaks
        // one GDI object until the process dies, and OnPaint builds ~20 of them
        // per repaint. Clone into a managed Font, then delete the HFONT.
        static Font MakePixelFont(string name, int pt)
        {
            IntPtr hf = Native.CreateFont(-(int)(pt * 96 / 72), 0, 0, 0, 400,
                0, 0, 0, 1, 0, 0, Native.NONANTIALIASED_QUALITY, 0, name);
            try { using (Font wrapped = Font.FromHfont(hf)) return (Font)wrapped.Clone(); }
            finally { if (hf != IntPtr.Zero) Native.DeleteObject(hf); }
        }

        static Font F(int pt)
        {
            return MakePixelFont("Verdana", pt);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.TextRenderingHint = TextRenderingHint.SingleBitPerPixelGridFit;
            g.SmoothingMode = SmoothingMode.None;
            g.InterpolationMode = InterpolationMode.NearestNeighbor;
            g.CompositingQuality = CompositingQuality.HighSpeed;
            g.Clear(Palette.BG);
            Hot.Clear();

            // title bar
            using (var b = new SolidBrush(Palette.SURFACE)) g.FillRectangle(b, 0, 0, Width, 20);
            DrawText(g, "problip", 8, 4, Palette.TEXT, 12, true);
            var xr = new Rectangle(Width - 20, 0, 20, 20);
            Hot.Add(MakeHot(xr, delegate() { Hide(); }));
            DrawText(g, "X", Width - 16, 4, Palette.TEXT2, 12, true);

            // status — only the truth, top-right. A broken sound asset is its own
            // state: reporting ON while nothing can play is the defect this
            // replaces.
            string st = Engine.IsBroken ? "ERR" : (Engine.IsOn ? "ON" : "OFF");
            Color stc = Engine.IsBroken ? Palette.DANGERTXT : (Engine.IsOn ? Palette.SUCCESS : Palette.MUTED);
            int sw = (int)g.MeasureString(st, F(12)).Width;
            DrawText(g, st, Width - 24 - sw, 4, stc, 12, true);

            // ── volume slider 0..100 ──
            int yv = 34;
            DrawText(g, "vol", 8, yv + 2, Palette.TEXT2, 11);
            VolTrack = new Rectangle(36, yv, 168, 12);
            DrawBevel(g, VolTrack, false);
            int pct = (int)Math.Round(S.Volume * 100);
            using (var bg = new SolidBrush(Palette.SURFACE)) g.FillRectangle(bg, VolTrack.X + 1, VolTrack.Y + 1, VolTrack.Width - 2, VolTrack.Height - 2);
            using (var fill = new SolidBrush(Palette.LINK))
                g.FillRectangle(fill, VolTrack.X + 1, VolTrack.Y + 1, (int)((VolTrack.Width - 2) * S.Volume), VolTrack.Height - 2);
            int thx = VolTrack.X + (int)((VolTrack.Width - 10) * S.Volume);
            var thr = new Rectangle(thx, yv - 3, 10, 18);
            Hot.Add(MakeHot(thr, delegate() { StartVolumeDrag(); }));
            DrawBevel(g, thr, true);
            using (var tb = new SolidBrush(Palette.ALT)) g.FillRectangle(tb, thr.X + 1, thr.Y + 1, thr.Width - 2, thr.Height - 2);
            DrawText(g, pct + "%", 210, yv + 1, Palette.TEXT, 11, true);

            // ── interval presets ──
            int yi = 60;
            DrawText(g, "sec", 8, yi + 4, Palette.TEXT2, 11);
            int xi = 32;
            int[] mins = new int[] { 4, 5, 10, 15, 20, 30 };
            int[] maxs = new int[] { 7, 5, 10, 15, 20, 30 };
            for (int i = 0; i < mins.Length; i++)
            {
                int mn = mins[i] * 1000, mx = maxs[i] * 1000;
                bool sel = S.MinMs == mn && S.MaxMs == mx;
                string lbl = mn == mx ? (mn / 1000) + "s" : (mn / 1000) + "-" + (mx / 1000);
                int w = TextW(g, lbl, 10) + 10;
                var r = new Rectangle(xi, yi, w, 22);
                int cmn = mn, cmx = mx;
                Hot.Add(MakeHot(r, delegate() { ApplyRange(cmn, cmx); }));
                DrawButton(g, r, lbl, sel, 10);
                xi += w + 3;
            }

            // ── bottom row: autostart + ON/OFF ──
            int yb = 96;
            bool ao = AutoStartEnabled();
            var ar = new Rectangle(8, yb, 120, 22);
            Hot.Add(MakeHot(ar, delegate() { ToggleAutostart(); }));
            DrawButton(g, ar, ao ? "[X] autostart" : "[ ] autostart", ao);

            var sr = new Rectangle(140, yb, 60, 22);
            Hot.Add(MakeHot(sr, delegate() { Engine.Start(); SyncTray(); Refresh(); }));
            DrawButton(g, sr, "ON", false);
            var pr = new Rectangle(204, yb, 60, 22);
            Hot.Add(MakeHot(pr, delegate() { Engine.Stop(); SyncTray(); Refresh(); }));
            DrawButton(g, pr, "OFF", false);

            // The reason, in the window, when there is one. Pressing ON with a
            // broken asset retries the load and this line either goes away or
            // says why it did not.
            string failure = Engine.FailureText;
            if (failure != null)
                DrawText(g, Truncate(g, failure, Width - 16, 10), 8, yb + 26, Palette.DANGERTXT, 10);
        }

        // Clip a message to the window instead of letting it run off the edge:
        // the point of showing the failure is that the user can read it.
        string Truncate(Graphics g, string s, int maxWidth, int pt)
        {
            if (TextW(g, s, pt) <= maxWidth) return s;
            while (s.Length > 4 && TextW(g, s.Substring(0, s.Length - 4) + "...", pt) > maxWidth)
                s = s.Substring(0, s.Length - 1);
            return s.Substring(0, Math.Max(1, s.Length - 4)) + "...";
        }

        static HotZone MakeHot(Rectangle r, Action a)
        {
            HotZone h = new HotZone();
            h.R = r;
            h.A = a;
            return h;
        }

        void DrawText(Graphics g, string s, int x, int y, Color c, int pt, bool bold = false)
        {
            using (Font f = F(pt))
            using (var br = new SolidBrush(c))
                g.DrawString(s, f, br, (float)x, (float)y);
        }

        void DrawBevel(Graphics g, Rectangle r, bool raised)
        {
            Color hi = raised ? Palette.BEVEL : Palette.BDARK;
            Color lo = raised ? Palette.BDARK : Palette.BEVEL;
            using (var p1 = new Pen(hi)) g.DrawRectangle(p1, r.X, r.Y, r.Width - 1, r.Height - 1);
            using (var p2 = new Pen(lo)) g.DrawRectangle(p2, r.X + 1, r.Y + 1, r.Width - 3, r.Height - 3);
        }

        void DrawButton(Graphics g, Rectangle r, string label, bool selected, int pt = 12)
        {
            using (var bg = new SolidBrush(selected ? Palette.COMPARE : Palette.RAISED))
                g.FillRectangle(bg, r.X + 2, r.Y + 2, r.Width - 4, r.Height - 4);
            DrawBevel(g, r, !selected);
            var fmt = new StringFormat();
            fmt.Alignment = StringAlignment.Center;
            fmt.LineAlignment = StringAlignment.Center;
            using (Font f = F(pt))
            using (var br = new SolidBrush(selected ? Palette.LINK : Palette.TEXT))
                g.DrawString(label, f, br, new RectangleF(r.X + 2, r.Y + 2, r.Width - 4, r.Height - 4), fmt);
        }

        // measure text width in px at given pt, for sizing buttons to their label
        int TextW(Graphics g, string s, int pt)
        {
            using (Font f = F(pt))
                return (int)Math.Ceiling(g.MeasureString(s, f).Width);
        }

        void StartVolumeDrag()
        {
            VolDragging = true;
        }

        void SetVolumeFromX(int x)
        {
            double v = (double)(x - VolTrack.X) / (VolTrack.Width - 10);
            if (v < 0) v = 0; else if (v > 1) v = 1;
            S.Volume = v;
            Refresh();
        }

        void EndVolumeDrag()
        {
            if (!VolDragging) return;
            VolDragging = false;
            S.Save("Volume", S.Volume.ToString("0.00", System.Globalization.CultureInfo.InvariantCulture));
            Engine.Reload();
            SyncTray();
            Refresh();
        }

        // The tray caption is the only thing that reports the engine's health
        // while the window is hidden, so any state change refreshes it.
        void SyncTray()
        {
            try { if (Tray != null) Tray.Text = Engine.TrayCaption; } catch { }
        }

        void ApplyRange(int mn, int mx)
        {
            S.MinMs = mn; S.MaxMs = mx;
            S.Save("MinMs", mn.ToString());
            S.Save("MaxMs", mx.ToString());
            Engine.MinMs = mn; Engine.MaxMs = mx;
            Refresh();
        }

        bool AutoStartEnabled()
        {
            try
            {
                using (RegistryKey rk = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run"))
                {
                    return rk != null && rk.GetValue("Problip") != null;
                }
            }
            catch { return false; }
        }

        void ToggleAutostart()
        {
            try
            {
                // problip.ini is the authoritative setting; the Run key is its
                // projection, reapplied from the INI on every launch (see Main).
                // Touching only the registry therefore looked like it worked and
                // was silently reverted the next time Problip started.
                bool enable = !AutoStartEnabled();
                S.AutoStart = enable;
                S.Save("AutoStart", enable ? "1" : "0");
                using (RegistryKey rk = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (rk == null) return;
                    if (enable) rk.SetValue("Problip", "\"" + Application.ExecutablePath + "\"");
                    else rk.DeleteValue("Problip", false);
                }
                Refresh();
            }
            catch { }
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button == MouseButtons.Left)
            {
                if (VolTrack.Contains(e.Location))
                {
                    SetVolumeFromX(e.X);
                    VolDragging = true;
                    return;
                }
                foreach (HotZone h in Hot)
                {
                    if (h.R.Contains(e.Location)) { h.A(); return; }
                }
            }
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            if (VolDragging && e.Button == MouseButtons.Left)
            {
                SetVolumeFromX(e.X);
            }
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            base.OnMouseUp(e);
            if (VolDragging)
            {
                EndVolumeDrag();
            }
        }

    }
    static class Program
    {
        static ProblipForm _form;

        [STAThread]
        static void Main()
        {
            bool createdNew;
            using (var mutex = new System.Threading.Mutex(true, "Local\\ProblipApp", out createdNew))
            {
                if (!createdNew) return;

                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);

                string dir = AppDomain.CurrentDomain.BaseDirectory;
                Settings s = new Settings(dir);
                s.Load();

                BlipEngine engine = new BlipEngine(s);
                engine.Start();

                NotifyIcon tray = new NotifyIcon();
                tray.Icon = AppIcon.For(s.IcoPath, SystemInformation.SmallIconSize.Width);
                tray.Text = TrayText(engine);
                ContextMenuStrip menu = new ContextMenuStrip();
                menu.Items.Add("Open settings", null, delegate(object o, EventArgs e) { ShowForm(s, engine, tray); });
                menu.Items.Add(new ToolStripSeparator());
                menu.Items.Add("Start", null, delegate(object o, EventArgs e) { engine.Start(); tray.Text = TrayText(engine); });
                menu.Items.Add("Stop", null, delegate(object o, EventArgs e) { engine.Stop(); tray.Text = TrayText(engine); });
                menu.Items.Add(new ToolStripSeparator());
                menu.Items.Add("Exit", null, delegate(object o, EventArgs e)
                {
                    engine.Cleanup();
                    tray.Visible = false;
                    Application.Exit();
                });
                tray.ContextMenuStrip = menu;
                tray.Visible = true;
                tray.DoubleClick += delegate(object o, EventArgs e) { ShowForm(s, engine, tray); };

                // always fix the autostart entry on every boot
                try
                {
                    using (RegistryKey rk = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                    {
                        if (rk != null)
                        {
                            if (s.AutoStart)
                                rk.SetValue("Problip", "\"" + Application.ExecutablePath + "\"");
                            else if (rk.GetValue("Problip") != null)
                                rk.DeleteValue("Problip", false);
                        }
                    }
                }
                catch { }

                Application.Run();
                engine.Cleanup();
                tray.Dispose();
            }
        }

        // NotifyIcon.Text rejects anything over 63 characters, so the failure
        // reason is named but the asset detail stays in the window.
        static string TrayText(BlipEngine engine)
        {
            return engine.TrayCaption;
        }

        static void ShowForm(Settings s, BlipEngine engine, NotifyIcon tray)
        {
            if (_form == null || _form.IsDisposed)
            {
                _form = new ProblipForm(s, engine, tray);
                _form.FormClosing += delegate(object o, FormClosingEventArgs e)
                {
                    if (e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; _form.Hide(); }
                };
            }
            _form.Show();
            _form.Activate();
        }
    }
}
