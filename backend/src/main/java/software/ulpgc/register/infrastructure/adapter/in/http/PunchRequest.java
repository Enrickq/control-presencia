
package software.ulpgc.register.infrastructure.adapter.in.http;

public record PunchRequest(
        String employeeId,
        String employeeName,
        String placeId,
        String placeName,
        String type
) {
}
