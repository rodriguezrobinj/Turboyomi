# Subproyecto 10b: motor JS de las extensiones

## 1. Objetivo
Segundo de 10a–10d. Las extensiones evalúan con `QuickJs` (GraalJS por debajo) JavaScript sacado de la web de la
fuente: un sitio comprometido u hostil no debe poder colgar un hilo para siempre ni salir del motor.

Evidencia (prueba real contra `QuickJs` de `master`):
- Con un objeto entregado por `set`, el script llega a `api.getClass().getClassLoader()`: `HostAccess.ALL` abre
  todos los métodos públicos del objeto y de lo que devuelven, y desde el cargador de clases, cualquier clase.
- `while (true) {}` no termina nunca: no hay límite de tiempo.

### Fuera de alcance
- Límite de memoria del script: GraalJS community no lo tiene (es del sandbox de la edición enterprise).
- `Duktape` (Rhino): no hay Rhino en el classpath, así que `getEngineByName("rhino")` da `null` y nunca evalúa nada.

## 2. Diseño
- `HostAccess.NONE`: el script no ve ningún objeto Java.
- `set(name, type, object)` entrega un `ProxyObject` con solo los métodos de `type`. Así lo hace el QuickJS real:
  la interfaz, no el objeto. Los argumentos se convierten al tipo de cada parámetro (`Value.as`).
- Límite de **30 s** por `evaluate`: un vigilante (un hilo daemon compartido) llama a `Context.interrupt`. El script
  falla con `QuickJsException`, y el motor sigue sirviendo para el siguiente. El contexto ya no se entra en el
  constructor: la interrupción espera a que todos los hilos salgan, y ese no saldría nunca.

## 3. Pruebas
- `QuickJsTest`: números, textos y arrays salen como antes; un método de la interfaz entregada se llama con sus
  argumentos; `getClass()` y `hashCode()` no existen para el script; `while (true) {}` se corta (límite de 0,5 s en
  la prueba) y el motor evalúa después.
