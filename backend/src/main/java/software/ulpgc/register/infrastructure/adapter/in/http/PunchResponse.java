
package software.ulpgc.register.infrastructure.adapter.in.http;

public record PunchResponse(
        String employeeId,
        String placeId,
        String type,
        String timestamp
) {
}
