$base = "D:\IdeaProjects\Word Agent\word-agent-web\frontend"
$head = Get-Content -Path "$base\concept-head.html" -Raw -Encoding UTF8
$p1 = Get-Content -Path "$base\concept-part1.html" -Raw -Encoding UTF8
$p2 = Get-Content -Path "$base\concept-part2.html" -Raw -Encoding UTF8
$p3 = Get-Content -Path "$base\concept-part3.html" -Raw -Encoding UTF8
$result = $head + $p1 + $p2 + $p3
Set-Content -Path "$base\concept.html" -Value $result -Encoding UTF8
Write-Host "Done: $($result.Length) chars written"