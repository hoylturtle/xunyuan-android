using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Windows.Forms;

// The only distributed file. The bundled runtime is extracted per launch and
// removed after the Java process exits. No installer or elevated rights.
internal static class PortableLauncher {
    [STAThread]
    private static int Main(string[] args) {
        string folder = null;
        try {
            if (args.Length > 1 || (args.Length == 1 && args[0] != "--self-test" && args[0] != "--smoke-test"))
                throw new ArgumentException("Unsupported launch option.");
            string expected = "PAYLOAD_SHA256";
            var assembly = Assembly.GetExecutingAssembly();
            using (Stream stream = assembly.GetManifestResourceStream("payload.zip")) {
                if (stream == null) throw new IOException("Bundled runtime missing.");
                using (var hash = SHA256.Create()) {
                    string actual = BitConverter.ToString(hash.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
                    if (actual != expected) throw new IOException("Bundled runtime checksum mismatch.");
                }
            }
            folder = Path.Combine(Path.GetTempPath(), "Xunyuan-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(folder);
            string prefix = Path.GetFullPath(folder) + Path.DirectorySeparatorChar;
            using (Stream stream = assembly.GetManifestResourceStream("payload.zip"))
            using (var archive = new ZipArchive(stream, ZipArchiveMode.Read)) {
                foreach (var entry in archive.Entries) {
                    string target = Path.GetFullPath(Path.Combine(folder, entry.FullName));
                    if (!target.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) throw new IOException("Invalid bundle path.");
                    if (entry.FullName.EndsWith("/")) { Directory.CreateDirectory(target); continue; }
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    entry.ExtractToFile(target, false);
                }
            }
            string java = Path.Combine(folder, "Xunyuan", "runtime", "bin", "javaw.exe");
            string jar = Path.Combine(folder, "Xunyuan", "app", "Xunyuan.jar");
            var start = new ProcessStartInfo(java, "-Dfile.encoding=UTF-8 -jar \"" + jar + "\"" + (args.Length == 1 ? " " + args[0] : ""));
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            using (var process = Process.Start(start)) {
                process.WaitForExit();
                return process.ExitCode;
            }
        } catch (Exception ex) {
            MessageBox.Show("寻源启动失败：\n" + ex.Message, "寻源", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        } finally {
            if (folder != null) {
                try { Directory.Delete(folder, true); } catch { /* The OS may still hold a runtime file handle. */ }
            }
        }
    }
}
