using System.Text.Json;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

internal static class Program
{
    private static ViGEmClient? client;
    private static IXbox360Controller? controller;

    private static void Main()
    {
        Console.OutputEncoding = System.Text.Encoding.UTF8;
        Console.WriteLine("{\"type\":\"ready\"}");

        while (true)
        {
            string? line = Console.ReadLine();

            if (string.IsNullOrWhiteSpace(line))
            {
                continue;
            }

            try
            {
                using JsonDocument document = JsonDocument.Parse(line);
                JsonElement root = document.RootElement;

                string type = root.GetProperty("type").GetString() ?? "";

                if (type == "connect")
                {
                    ConnectController();
                }
                else if (type == "disconnect")
                {
                    DisconnectController();
                }
                else if (type == "state")
                {
                    EnsureConnected();
                    ApplyState(root);
                }
                else if (type == "exit")
                {
                    DisconnectController();
                    return;
                }
                else
                {
                    WriteError("Unknown command type: " + type);
                }
            }
            catch (Exception ex)
            {
                WriteError(ex.GetType().Name + ": " + ex.Message);
            }
        }
    }

    private static void ConnectController()
    {
        if (controller != null)
        {
            Console.WriteLine("{\"type\":\"connected\",\"already\":true}");
            return;
        }

        client ??= new ViGEmClient();

        controller = client.CreateXbox360Controller();
        controller.Connect();

        ResetController();

        Console.WriteLine("{\"type\":\"connected\"}");
    }

    private static void EnsureConnected()
    {
        if (controller == null)
        {
            ConnectController();
        }
    }

    private static void DisconnectController()
    {
        if (controller != null)
        {
            try
            {
                ResetController();
                controller.Disconnect();
            }
            catch
            {
                // ignore disconnect errors
            }

            controller = null;
        }

        if (client != null)
        {
            client.Dispose();
            client = null;
        }

        Console.WriteLine("{\"type\":\"disconnected\"}");
    }

    private static void ApplyState(JsonElement root)
    {
        if (controller == null)
        {
            return;
        }

        double lx = Clamp(GetDouble(root, "lx"), -1.0, 1.0);
        double ly = Clamp(GetDouble(root, "ly"), -1.0, 1.0);
        double rx = Clamp(GetDouble(root, "rx"), -1.0, 1.0);
        double ry = Clamp(GetDouble(root, "ry"), -1.0, 1.0);

        controller.SetAxisValue(Xbox360Axis.LeftThumbX, ToAxis(lx));
        controller.SetAxisValue(Xbox360Axis.LeftThumbY, ToAxis(-ly));
        controller.SetAxisValue(Xbox360Axis.RightThumbX, ToAxis(rx));
        controller.SetAxisValue(Xbox360Axis.RightThumbY, ToAxis(-ry));

        controller.SetSliderValue(Xbox360Slider.LeftTrigger, ToTrigger(GetDouble(root, "lt")));
        controller.SetSliderValue(Xbox360Slider.RightTrigger, ToTrigger(GetDouble(root, "rt")));

        SetButton(Xbox360Button.A, GetBool(root, "a"));
        SetButton(Xbox360Button.B, GetBool(root, "b"));
        SetButton(Xbox360Button.X, GetBool(root, "x"));
        SetButton(Xbox360Button.Y, GetBool(root, "y"));

        SetButton(Xbox360Button.LeftShoulder, GetBool(root, "lb"));
        SetButton(Xbox360Button.RightShoulder, GetBool(root, "rb"));

        SetButton(Xbox360Button.Back, GetBool(root, "back"));
        SetButton(Xbox360Button.Start, GetBool(root, "start"));
        SetButton(Xbox360Button.Guide, GetBool(root, "guide"));

        SetButton(Xbox360Button.LeftThumb, GetBool(root, "ls"));
        SetButton(Xbox360Button.RightThumb, GetBool(root, "rs"));

        SetButton(Xbox360Button.Up, GetBool(root, "dpadUp"));
        SetButton(Xbox360Button.Down, GetBool(root, "dpadDown"));
        SetButton(Xbox360Button.Left, GetBool(root, "dpadLeft"));
        SetButton(Xbox360Button.Right, GetBool(root, "dpadRight"));
    }

    private static void ResetController()
    {
        if (controller == null)
        {
            return;
        }

        controller.SetAxisValue(Xbox360Axis.LeftThumbX, 0);
        controller.SetAxisValue(Xbox360Axis.LeftThumbY, 0);
        controller.SetAxisValue(Xbox360Axis.RightThumbX, 0);
        controller.SetAxisValue(Xbox360Axis.RightThumbY, 0);

        controller.SetSliderValue(Xbox360Slider.LeftTrigger, 0);
        controller.SetSliderValue(Xbox360Slider.RightTrigger, 0);

        SetButton(Xbox360Button.A, false);
        SetButton(Xbox360Button.B, false);
        SetButton(Xbox360Button.X, false);
        SetButton(Xbox360Button.Y, false);

        SetButton(Xbox360Button.LeftShoulder, false);
        SetButton(Xbox360Button.RightShoulder, false);

        SetButton(Xbox360Button.Back, false);
        SetButton(Xbox360Button.Start, false);
        SetButton(Xbox360Button.Guide, false);

        SetButton(Xbox360Button.LeftThumb, false);
        SetButton(Xbox360Button.RightThumb, false);

        SetButton(Xbox360Button.Up, false);
        SetButton(Xbox360Button.Down, false);
        SetButton(Xbox360Button.Left, false);
        SetButton(Xbox360Button.Right, false);
    }

    private static void SetButton(Xbox360Button button, bool pressed)
    {
        controller?.SetButtonState(button, pressed);
    }

    private static short ToAxis(double value)
    {
        value = Clamp(value, -1.0, 1.0);
        return (short)Math.Round(value * short.MaxValue);
    }

    private static byte ToTrigger(double value)
    {
        value = Clamp(value, 0.0, 1.0);
        return (byte)Math.Round(value * byte.MaxValue);
    }

    private static double GetDouble(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out JsonElement property))
        {
            return 0.0;
        }

        return property.ValueKind == JsonValueKind.Number && property.TryGetDouble(out double value)
            ? value
            : 0.0;
    }

    private static bool GetBool(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out JsonElement property))
        {
            return false;
        }

        return property.ValueKind == JsonValueKind.True;
    }

    private static double Clamp(double value, double min, double max)
    {
        return Math.Max(min, Math.Min(max, value));
    }

    private static void WriteError(string message)
    {
        string safe = message
            .Replace("\\", "\\\\")
            .Replace("\"", "'")
            .Replace("\r", " ")
            .Replace("\n", " ");

        Console.WriteLine("{\"type\":\"error\",\"message\":\"" + safe + "\"}");
    }
}
