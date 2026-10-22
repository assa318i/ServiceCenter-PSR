$ErrorActionPreference = 'Stop'
$out = Join-Path $PSScriptRoot 'scan.bmp'
$dm = New-Object -ComObject WIA.DeviceManager
if ($dm.DeviceInfos.Count -lt 1) { throw 'no WIA device' }
$dev = $dm.DeviceInfos.Item(1).Connect()
$it = $dev.Items.Item(1)
$it.Properties.Item('Horizontal Resolution').Value = 300
$it.Properties.Item('Vertical Resolution').Value = 300
$hr = $it.Properties.Item('Horizontal Resolution').Value
$he = $it.Properties.Item('Horizontal Extent').Value
$ve = $it.Properties.Item('Vertical Extent').Value
Write-Output "scan at $hr dpi, extent $he x $ve"
$img = $it.Transfer('Scan')
Write-Output "captured $($img.Width) x $($img.Height) depth $($img.BitsPerChannel)"
$img.SaveFile($out)
Write-Output "saved $out"
