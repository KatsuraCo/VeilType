Add-Type -AssemblyName System.Drawing

$RootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$OutDir = Join-Path $PSScriptRoot "product-hunt"
New-Item -ItemType Directory -Force $OutDir | Out-Null

$Shots = @{
    Top = Join-Path $PSScriptRoot "raw\02-app-top.png"
    Mid = Join-Path $PSScriptRoot "raw\01-app-home.png"
    Home = Join-Path $PSScriptRoot "raw\phone-current.png"
}

function Add-RoundRect($Graphics, [System.Drawing.Rectangle] $Rect, [int] $Radius, $Brush, $Pen = $null) {
    $Path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $Path.AddArc($Rect.X, $Rect.Y, $Radius, $Radius, 180, 90)
    $Path.AddArc($Rect.Right - $Radius, $Rect.Y, $Radius, $Radius, 270, 90)
    $Path.AddArc($Rect.Right - $Radius, $Rect.Bottom - $Radius, $Radius, $Radius, 0, 90)
    $Path.AddArc($Rect.X, $Rect.Bottom - $Radius, $Radius, $Radius, 90, 90)
    $Path.CloseFigure()
    $Graphics.FillPath($Brush, $Path)
    if ($Pen) { $Graphics.DrawPath($Pen, $Path) }
    $Path.Dispose()
}

function New-LaunchImage($Name, $Title, $Subtitle, [string[]] $Chips, $PhoneImage, $Gold = $false) {
    $Canvas = New-Object System.Drawing.Bitmap 1600, 900
    $G = [System.Drawing.Graphics]::FromImage($Canvas)
    $G.SmoothingMode = "AntiAlias"
    $G.TextRenderingHint = "AntiAliasGridFit"

    $Cyan = [System.Drawing.Color]::FromArgb(110, 231, 242)
    $GoldColor = [System.Drawing.Color]::FromArgb(240, 210, 142)
    $Accent = if ($Gold) { $GoldColor } else { $Cyan }
    $White = [System.Drawing.Color]::FromArgb(246, 248, 252)
    $Muted = [System.Drawing.Color]::FromArgb(166, 176, 195)

    $Bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush `
        ([System.Drawing.Rectangle]::new(0, 0, 1600, 900)),
        ([System.Drawing.Color]::FromArgb(6, 9, 18)),
        ([System.Drawing.Color]::FromArgb(15, 26, 48)),
        38
    $G.FillRectangle($Bg, 0, 0, 1600, 900)
    $G.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(38, $Accent))), -180, -160, 610, 610)
    $G.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(28, $GoldColor))), 1120, 520, 580, 580)

    $BrandFont = New-Object System.Drawing.Font "Segoe UI", 28, ([System.Drawing.FontStyle]::Bold)
    $TitleFont = New-Object System.Drawing.Font "Segoe UI", 76, ([System.Drawing.FontStyle]::Bold)
    $SubFont = New-Object System.Drawing.Font "Segoe UI", 28, ([System.Drawing.FontStyle]::Regular)
    $ChipFont = New-Object System.Drawing.Font "Segoe UI", 22, ([System.Drawing.FontStyle]::Bold)

    $G.DrawString("VeilType", $BrandFont, (New-Object System.Drawing.SolidBrush $Accent), 90, 72)
    $G.DrawString($Title, $TitleFont, (New-Object System.Drawing.SolidBrush $White), [System.Drawing.RectangleF]::new(90, 168, 820, 250))
    $G.DrawString($Subtitle, $SubFont, (New-Object System.Drawing.SolidBrush $Muted), [System.Drawing.RectangleF]::new(96, 430, 730, 150))

    $X = 96
    $Y = 620
    foreach ($Chip in $Chips) {
        $Size = $G.MeasureString($Chip, $ChipFont)
        $Rect = [System.Drawing.Rectangle]::new($X, $Y, [int]($Size.Width + 42), 54)
        Add-RoundRect $G $Rect 24 `
            (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(28, 255, 255, 255))) `
            (New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(80, $Accent), 2))
        $G.DrawString($Chip, $ChipFont, (New-Object System.Drawing.SolidBrush $White), $X + 21, $Y + 10)
        $X += $Rect.Width + 16
        if ($X -gt 760) { $X = 96; $Y += 70 }
    }

    $Img = [System.Drawing.Image]::FromFile($PhoneImage)
    $Frame = [System.Drawing.Rectangle]::new(1078, 38, 390, 845)
    $Inner = [System.Drawing.Rectangle]::new(1096, 56, 354, 809)
    $G.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 0, 0, 0))), 1018, 760, 510, 120)
    Add-RoundRect $G $Frame 48 `
        (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(12, 18, 32))) `
        (New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(120, 166, 176, 195), 4))

    $Clip = New-Object System.Drawing.Drawing2D.GraphicsPath
    $Clip.AddArc($Inner.X, $Inner.Y, 34, 34, 180, 90)
    $Clip.AddArc($Inner.Right - 34, $Inner.Y, 34, 34, 270, 90)
    $Clip.AddArc($Inner.Right - 34, $Inner.Bottom - 34, 34, 34, 0, 90)
    $Clip.AddArc($Inner.X, $Inner.Bottom - 34, 34, 34, 90, 90)
    $Clip.CloseFigure()
    $State = $G.Save()
    $G.SetClip($Clip)
    $SrcRatio = $Img.Width / $Img.Height
    $DstRatio = $Inner.Width / $Inner.Height
    if ($SrcRatio -gt $DstRatio) {
        $SrcH = $Img.Height
        $SrcW = [int]($SrcH * $DstRatio)
        $SrcX = [int](($Img.Width - $SrcW) / 2)
        $SrcY = 0
    } else {
        $SrcW = $Img.Width
        $SrcH = [int]($SrcW / $DstRatio)
        $SrcX = 0
        $SrcY = 0
    }
    $G.DrawImage($Img, $Inner, [System.Drawing.Rectangle]::new($SrcX, $SrcY, $SrcW, $SrcH), [System.Drawing.GraphicsUnit]::Pixel)
    $G.Restore($State)
    $Img.Dispose()

    $Canvas.Save((Join-Path $OutDir $Name), [System.Drawing.Imaging.ImageFormat]::Png)
    $G.Dispose()
    $Canvas.Dispose()
}

New-LaunchImage "01-hero-encrypt-before-send.png" "Encrypt before you send." "A private Android keyboard for Telegram, WhatsApp, Signal, SMS and any text field." @("No server", "No account", "No cloud") $Shots.Top
New-LaunchImage "02-how-it-works.png" "One key. Any chat." "Create an 8-emoji shared key once, then encrypt inside the messenger people already use." @("8-emoji key", "Local only", "Normal chats") $Shots.Mid $true
New-LaunchImage "03-real-app-screen.png" "Not another messenger." "VeilType is the privacy layer where typing already happens." @("Keyboard layer", "Android-first", "APK launch") $Shots.Top
New-LaunchImage "04-capsules.png" "Text first. Capsules next." "Voice, photo and video capsules extend the same local encryption model." @("Voice", "Photo", "Video") $Shots.Mid $true
New-LaunchImage "05-trust-model.png" "Clear promises. No fake magic." "Local encryption, no recovery, and no cloud account pretending to be private." @("No recovery", "Shared key", "Local keys") $Shots.Home
