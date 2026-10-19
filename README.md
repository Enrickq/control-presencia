# Control de Presencia

Proyecto individual de Ingeniería del Software II de la Universidad de Las Palmas de Gran Canaria.

## Descripción

Sistema de control de presencia orientado al registro de entradas y salidas de empleados, la gestión de horarios y la
consulta de asistencia.

## Estado actual

Se ha desarrollado el modelo inicial de registro de presencia, tomando como punto de partida el código trabajado en
clase.

La implementación actual incluye:

- Representación de empleados y lugares.
- Modelado de eventos de entrada y salida.
- Abstracción del almacenamiento mediante `EventStore`.
- Persistencia de eventos en archivos mediante `FileEventStore`.
- Pruebas unitarias con JUnit y AssertJ.

## Tecnologías

- Java 27
- Maven
- JUnit 5
- AssertJ

## Compilación y pruebas

Para ejecutar las pruebas desde la raíz del proyecto:

```bash
mvn clean test
```

## Estructura

- `model`: representación de los datos del dominio.
- `io`: interfaz y adaptación de la persistencia.
- `src/test`: pruebas automatizadas.

## Alcance actual

Esta primera versión implementa el registro persistente de eventos. La gestión de horarios, la API HTTP y las interfaces
de usuario todavía no forman parte de esta versión.