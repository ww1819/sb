class AuthUser {
  const AuthUser({
    required this.token,
    required this.userId,
    required this.username,
    required this.tenantId,
    required this.tenantCode,
    required this.schemaName,
    this.realName,
    this.roles,
    this.permissions,
  });

  final String token;
  final String userId;
  final String username;
  final String tenantId;
  final String tenantCode;
  final String schemaName;
  final String? realName;
  final List<String>? roles;
  final Map<String, dynamic>? permissions;

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    List<String>? roles;
    if (json['roles'] is List) {
      roles = (json['roles'] as List).map((e) => e.toString()).toList();
    }
    Map<String, dynamic>? permissions;
    if (json['permissions'] is Map) {
      permissions = Map<String, dynamic>.from(json['permissions'] as Map);
    }
    return AuthUser(
      token: json['token']?.toString() ?? '',
      userId: json['userId']?.toString() ?? '',
      username: json['username']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      tenantCode: json['tenantCode']?.toString() ?? '',
      schemaName: json['schemaName']?.toString() ?? '',
      realName: json['realName']?.toString(),
      roles: roles,
      permissions: permissions,
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
        'roles': roles,
        'permissions': permissions,
      };
}
