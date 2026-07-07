class AuthUser {
  const AuthUser({
    required this.token,
    required this.userId,
    required this.username,
    required this.tenantId,
    required this.tenantCode,
    required this.schemaName,
    this.realName,
  });

  final String token;
  final String userId;
  final String username;
  final String tenantId;
  final String tenantCode;
  final String schemaName;
  final String? realName;

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(
      token: json['token']?.toString() ?? '',
      userId: json['userId']?.toString() ?? '',
      username: json['username']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      tenantCode: json['tenantCode']?.toString() ?? '',
      schemaName: json['schemaName']?.toString() ?? '',
      realName: json['realName']?.toString(),
    );
  }

  Map<String, dynamic> toJson() => {
        'token': token,
        'userId': userId,
        'username': username,
        'tenantId': tenantId,
        'tenantCode': tenantCode,
        'schemaName': schemaName,
        'realName': realName,
      };
}
