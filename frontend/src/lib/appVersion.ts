/**
 * 서버에 올라간 화면 버전(version.json)이 지금 열린 화면과 다른지 확인한다.
 * 개발 서버에는 version.json이 없고, 확인할 수 없을 때도 새 버전이 없다고 본다.
 */
export async function hasNewVersion(): Promise<boolean> {
  if (import.meta.env.DEV) return false
  try {
    const response = await fetch('/version.json', { cache: 'no-store' })
    if (!response.ok) return false
    const { version } = (await response.json()) as { version?: unknown }
    return typeof version === 'string' && version !== __APP_VERSION__
  } catch {
    return false
  }
}
