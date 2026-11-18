/** Fails fast with a clear message when the backend is not running. */
export default async function globalSetup() {
  try {
    const res = await fetch('http://localhost:8080/actuator/health');
    if (!res.ok) throw new Error(`health returned ${res.status}`);
  } catch (e) {
    throw new Error(
      `Backend not reachable on http://localhost:8080 (${(e as Error).message}). ` +
        'Start it first: cd backend && mvnw spring-boot:test-run',
    );
  }
}
