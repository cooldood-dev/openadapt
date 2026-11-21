# Adapt+ now-playing reader.
# Reads the Windows System Media Transport Controls (whatever Spotify / a browser / any player reports)
# and prints one tab-separated line per poll:  title \t artist \t album \t positionMs \t durationMs \t playing(1/0) \t coverPath
# Prints "none" when nothing is playing. $parentId is prepended by the Java side; the loop exits when that PID dies.

$ErrorActionPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
if (-not (Get-Variable -Name parentId -Scope Global -ErrorAction SilentlyContinue)) { $parentId = 0 }

try { Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null } catch { return }

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, $type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
    $task.Result
}

try {
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
} catch { return }

$manager = $null
try {
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {}
if ($null -eq $manager) { return }

$coverDir = Join-Path $env:TEMP 'adaptplus-media'
try { New-Item -ItemType Directory -Force -Path $coverDir | Out-Null } catch {}

$lastKey = ''
$lastCover = ''

while ($true) {
    if ($parentId -ne 0 -and -not (Get-Process -Id $parentId -ErrorAction SilentlyContinue)) { break }

    $line = 'none'
    try {
        $session = $manager.GetCurrentSession()
        if ($null -ne $session) {
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()

            $title = [string]$props.Title
            $artist = [string]$props.Artist
            $album = [string]$props.AlbumTitle

            $start = [long]$timeline.StartTime.TotalMilliseconds
            $end = [long]$timeline.EndTime.TotalMilliseconds
            $pos = [long]$timeline.Position.TotalMilliseconds - $start
            $duration = [Math]::Max(0, $end - $start)

            $status = $playback.PlaybackStatus
            $playing = if ($status -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing) { '1' } else { '0' }

            $key = "$title|$artist"
            $cover = $lastCover
            if ($key -ne $lastKey) {
                $lastKey = $key
                $cover = ''
                try {
                    if ($props.Thumbnail) {
                        $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                        if ($stream -and $stream.Size -gt 0) {
                            $size = [uint32]$stream.Size
                            $reader = [Windows.Storage.Streams.DataReader]::new($stream)
                            Await ($reader.LoadAsync($size)) ([uint32]) | Out-Null
                            $bytes = New-Object byte[] $size
                            $reader.ReadBytes($bytes)
                            $reader.Dispose()
                            $stream.Dispose()
                            $hash = [Math]::Abs($key.GetHashCode())
                            $path = Join-Path $coverDir ("cover_$hash.img")
                            [System.IO.File]::WriteAllBytes($path, $bytes)
                            $cover = $path
                        }
                    }
                } catch { $cover = '' }
                $lastCover = $cover
            }

            if ($title -ne '') {
                $line = ($title, $artist, $album, $pos, $duration, $playing, $cover) -join "`t"
            }
        }
    } catch { $line = 'none' }

    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 500
}
